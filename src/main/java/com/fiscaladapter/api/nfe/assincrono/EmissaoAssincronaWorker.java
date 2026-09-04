package com.fiscaladapter.api.nfe.assincrono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.NfeEmissaoService;
import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import com.fiscaladapter.api.nfe.NfeResponse;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.seguranca.ClienteApiService;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import com.fiscaladapter.webhook.WebhookEventoPayload;
import com.fiscaladapter.webhook.WebhookNotifierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Processa a fila de emissoes assincronas de NFe (FIS-25/FIS-30): poll
 * periodico simples (sem broker externo - RabbitMQ/SQS ficam para quando o
 * volume justificar a complexidade operacional adicional) sobre a tabela
 * emissao_assincrona, um lote pequeno por vez para nao monopolizar a
 * conexao com a SEFAZ.
 *
 * Falha transitoria (SefazComunicacaoException - problema de comunicacao,
 * nao do documento em si) reagenda o job automaticamente com backoff
 * exponencial, ate MAX_TENTATIVAS_PROCESSAMENTO; esgotadas as tentativas, ou
 * para qualquer outro tipo de erro (dado invalido, regra de negocio violada
 * - nao adianta tentar de novo sem o cliente corrigir o pedido), o job vai
 * direto para FALHA.
 */
@Component
public class EmissaoAssincronaWorker {

    private static final Logger log = LoggerFactory.getLogger(EmissaoAssincronaWorker.class);
    private static final int MAX_TENTATIVAS_PROCESSAMENTO = 5;
    private static final Duration BACKOFF_BASE = Duration.ofSeconds(30);

    private final EmissaoAssincronaRepository repository;
    private final NfeEmissaoService nfeEmissaoService;
    private final ObjectMapper objectMapper;
    private final CriptografiaEmRepousoService criptografiaEmRepousoService;
    private final ClienteApiService clienteApiService;
    private final WebhookNotifierService webhookNotifierService;
    private final TransactionTemplate transactionTemplate;

    public EmissaoAssincronaWorker(EmissaoAssincronaRepository repository, NfeEmissaoService nfeEmissaoService,
                                    ObjectMapper objectMapper, CriptografiaEmRepousoService criptografiaEmRepousoService,
                                    ClienteApiService clienteApiService, WebhookNotifierService webhookNotifierService,
                                    PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.nfeEmissaoService = nfeEmissaoService;
        this.objectMapper = objectMapper;
        this.criptografiaEmRepousoService = criptografiaEmRepousoService;
        this.clienteApiService = clienteApiService;
        this.webhookNotifierService = webhookNotifierService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${fiscaladapter.assincrono.intervalo-poll-ms:5000}")
    public void processarPendentes() {
        List<EmissaoAssincrona> elegiveis = repository.buscarElegiveis(
                StatusEmissaoAssincrona.PENDENTE, Instant.now(), PageRequest.of(0, 5));
        for (EmissaoAssincrona job : elegiveis) {
            // Reivindicacao atomica (FIS-74): com mais de uma instancia da aplicacao rodando, duas
            // podem enxergar o mesmo job como elegivel no mesmo ciclo de poll - so quem vencer a
            // corrida (UPDATE condicional que realmente mudou a linha) processa; a outra ignora.
            if (marcarProcessando(job.getId())) {
                processar(job.getId());
            }
        }
    }

    private void processar(Long jobId) {
        NfeResponse resultado = null;
        String erroTerminal = null;
        try {
            NfePedidoEmissaoRequest pedido = carregarPedido(jobId);
            String clientId = transactionTemplate.execute(status -> repository.findById(jobId).orElseThrow().getClientId());
            resultado = nfeEmissaoService.processar(pedido, clientId);
            concluir(jobId, resultado);
        } catch (SefazComunicacaoException e) {
            if (reagendarSePossivel(jobId, e.getMessage())) {
                return; // volta para PENDENTE - nao e um estado terminal, nao notifica webhook ainda
            }
            erroTerminal = "Esgotadas as tentativas de comunicacao com a SEFAZ: " + e.getMessage();
            log.error("Emissao assincrona {} esgotou as tentativas de reprocessamento", jobId, e);
            falhar(jobId, erroTerminal);
        } catch (Exception e) {
            erroTerminal = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("Falha ao processar emissao assincrona {}", jobId, e);
            falhar(jobId, erroTerminal);
        }

        notificarWebhook(jobId, resultado, erroTerminal);
    }

    /** @return true se reagendou (ainda ha tentativas disponiveis), false se esgotou (chamador deve marcar FALHA). */
    private boolean reagendarSePossivel(Long jobId, String mensagemErro) {
        return transactionTemplate.execute(status -> {
            EmissaoAssincrona job = repository.findById(jobId).orElseThrow();
            if (job.getTentativasProcessamento() >= MAX_TENTATIVAS_PROCESSAMENTO) {
                return false;
            }
            Instant agora = Instant.now();
            long segundosBackoff = BACKOFF_BASE.toSeconds() * (1L << job.getTentativasProcessamento()); // backoff exponencial: 30s, 60s, 120s...
            job.reagendarAposFalhaTransitoria(mensagemErro, agora, agora.plusSeconds(segundosBackoff));
            log.warn("Emissao assincrona {} sera reprocessada em {}s (tentativa {}/{}): {}",
                    jobId, segundosBackoff, job.getTentativasProcessamento(), MAX_TENTATIVAS_PROCESSAMENTO, mensagemErro);
            return true;
        });
    }

    private NfePedidoEmissaoRequest carregarPedido(Long jobId) {
        String pedidoJson = transactionTemplate.execute(status -> repository.findById(jobId).orElseThrow().getPedidoJson());
        try {
            return objectMapper.readValue(criptografiaEmRepousoService.descriptografar(pedidoJson), NfePedidoEmissaoRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar pedido da emissao assincrona " + jobId, e);
        }
    }

    /** @return true se esta chamada reivindicou o job (era PENDENTE no momento exato do UPDATE). */
    private boolean marcarProcessando(Long jobId) {
        Integer linhasAtualizadas = transactionTemplate.execute(status ->
                repository.reivindicarSePendente(jobId, Instant.now()));
        return linhasAtualizadas != null && linhasAtualizadas == 1;
    }

    private void concluir(Long jobId, NfeResponse resultado) {
        String resultadoCriptografado = criptografiaEmRepousoService.criptografar(serializar(resultado));
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(jobId).ifPresent(job -> job.concluir(resultadoCriptografado, Instant.now())));
    }

    private void falhar(Long jobId, String mensagemErro) {
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(jobId).ifPresent(job -> job.falhar(mensagemErro, Instant.now())));
    }

    private void notificarWebhook(Long jobId, NfeResponse resultado, String erro) {
        String clientId = transactionTemplate.execute(status -> repository.findById(jobId).orElseThrow().getClientId());
        String webhookUrl = clienteApiService.obterWebhookUrl(clientId);
        if (webhookUrl == null) {
            return;
        }
        String webhookSecret = clienteApiService.obterWebhookSecret(clientId);

        WebhookEventoPayload payload = montarPayload(jobId, resultado, erro);
        boolean entregue = webhookNotifierService.notificar(webhookUrl, webhookSecret, payload);
        if (!entregue) {
            log.warn("Webhook da emissao assincrona {} nao pode ser entregue - cliente pode consultar via GET /api/v1/nfe/assincrono/{}", jobId, jobId);
        }
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(jobId).ifPresent(job -> job.incrementarTentativaNotificacao(Instant.now())));
    }

    private WebhookEventoPayload montarPayload(Long jobId, NfeResponse resultado, String erro) {
        String eventoId = UUID.randomUUID().toString();
        if (erro != null) {
            return new WebhookEventoPayload(eventoId, "nfe.falha", jobId, null, false, null, null, null, erro);
        }
        String tipo = resultado.autorizada() ? "nfe.autorizada" : "nfe.rejeitada";
        return new WebhookEventoPayload(eventoId, tipo, jobId, resultado.chaveAcesso(), resultado.autorizada(),
                resultado.codigoStatusSefaz(), resultado.motivoSefaz(), resultado.numeroProtocolo(), null);
    }

    private String serializar(NfeResponse resultado) {
        try {
            return objectMapper.writeValueAsString(resultado);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar resultado da emissao assincrona", e);
        }
    }
}
