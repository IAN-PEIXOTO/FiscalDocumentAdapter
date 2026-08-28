package com.fiscaladapter.api.nfe.assincrono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.NfeEmissaoService;
import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import com.fiscaladapter.api.nfe.NfeResponse;
import com.fiscaladapter.seguranca.ClienteApiService;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import com.fiscaladapter.webhook.WebhookEventoPayload;
import com.fiscaladapter.webhook.WebhookNotifierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

/**
 * Processa a fila de emissoes assincronas de NFe (FIS-25): poll periodico
 * simples (sem broker externo - RabbitMQ/SQS ficam para quando o volume
 * justificar a complexidade operacional adicional) sobre a tabela
 * emissao_assincrona, um lote pequeno por vez para nao monopolizar a
 * conexao com a SEFAZ.
 */
@Component
public class EmissaoAssincronaWorker {

    private static final Logger log = LoggerFactory.getLogger(EmissaoAssincronaWorker.class);

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
        List<EmissaoAssincrona> pendentes = repository.findTop5ByStatusOrderByCriadoEmAsc(StatusEmissaoAssincrona.PENDENTE);
        for (EmissaoAssincrona job : pendentes) {
            processar(job.getId());
        }
    }

    private void processar(Long jobId) {
        marcarProcessando(jobId);

        NfeResponse resultado = null;
        String erro = null;
        try {
            NfePedidoEmissaoRequest pedido = carregarPedido(jobId);
            String clientId = transactionTemplate.execute(status -> repository.findById(jobId).orElseThrow().getClientId());
            resultado = nfeEmissaoService.processar(pedido, clientId);
            concluir(jobId, resultado);
        } catch (Exception e) {
            erro = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("Falha ao processar emissao assincrona {}", jobId, e);
            falhar(jobId, erro);
        }

        notificarWebhook(jobId, resultado, erro);
    }

    private NfePedidoEmissaoRequest carregarPedido(Long jobId) {
        String pedidoJson = transactionTemplate.execute(status -> repository.findById(jobId).orElseThrow().getPedidoJson());
        try {
            return objectMapper.readValue(criptografiaEmRepousoService.descriptografar(pedidoJson), NfePedidoEmissaoRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar pedido da emissao assincrona " + jobId, e);
        }
    }

    private void marcarProcessando(Long jobId) {
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(jobId).ifPresent(job -> job.marcarProcessando(Instant.now())));
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

        WebhookEventoPayload payload = montarPayload(jobId, resultado, erro);
        boolean entregue = webhookNotifierService.notificar(webhookUrl, payload);
        if (!entregue) {
            log.warn("Webhook da emissao assincrona {} nao pode ser entregue - cliente pode consultar via GET /api/v1/nfe/assincrono/{}", jobId, jobId);
        }
        transactionTemplate.executeWithoutResult(status ->
                repository.findById(jobId).ifPresent(job -> job.incrementarTentativaNotificacao(Instant.now())));
    }

    private WebhookEventoPayload montarPayload(Long jobId, NfeResponse resultado, String erro) {
        if (erro != null) {
            return new WebhookEventoPayload("nfe.falha", jobId, null, false, null, null, null, erro);
        }
        String tipo = resultado.autorizada() ? "nfe.autorizada" : "nfe.rejeitada";
        return new WebhookEventoPayload(tipo, jobId, resultado.chaveAcesso(), resultado.autorizada(),
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
