package com.fiscaladapter.api.nfe.assincrono;

import com.fiscaladapter.api.nfe.NfeResponse;
import com.fiscaladapter.seguranca.ClienteApiService;
import com.fiscaladapter.webhook.WebhookEventoPayload;
import com.fiscaladapter.webhook.WebhookNotifierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

/**
 * Notificacao de webhook da emissao assincrona, extraida de EmissaoAssincronaWorker (FIS-79) para
 * rodar num Executor dedicado (@Async("webhookExecutor")) em vez de segurar a thread do scheduler
 * que processa a fila - a notificacao pode levar ate ~14s de backoff entre tentativas
 * (WebhookNotifierService: 2s+4s+8s), tempo que antes atrasava o processamento dos proximos jobs
 * do mesmo lote.
 *
 * @Async so funciona em chamadas vindas de FORA da classe (o proxy do Spring nao intercepta
 * self-invocation) - por isso esta logica precisou virar um componente separado, injetado no
 * worker, em vez de continuar como um metodo privado dele.
 */
@Service
public class WebhookNotificacaoAssincronaService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificacaoAssincronaService.class);

    private final EmissaoAssincronaRepository repository;
    private final ClienteApiService clienteApiService;
    private final WebhookNotifierService webhookNotifierService;
    private final TransactionTemplate transactionTemplate;

    public WebhookNotificacaoAssincronaService(EmissaoAssincronaRepository repository, ClienteApiService clienteApiService,
                                                WebhookNotifierService webhookNotifierService,
                                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.clienteApiService = clienteApiService;
        this.webhookNotifierService = webhookNotifierService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("webhookExecutor")
    public void notificar(Long jobId, NfeResponse resultado, String erro) {
        try {
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
        } catch (Exception e) {
            log.error("Falha inesperada ao notificar webhook da emissao assincrona {}", jobId, e);
        }
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
}
