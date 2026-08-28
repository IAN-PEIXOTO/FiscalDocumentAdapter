package com.fiscaladapter.api.nfe.assincrono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

/** Enfileira pedidos de emissao de NFe para processamento assincrono (FIS-25) e consulta seu status/resultado. */
@Service
public class EmissaoAssincronaService {

    private final EmissaoAssincronaRepository repository;
    private final ObjectMapper objectMapper;
    private final CriptografiaEmRepousoService criptografiaEmRepousoService;
    private final TransactionTemplate transactionTemplate;

    public EmissaoAssincronaService(EmissaoAssincronaRepository repository, ObjectMapper objectMapper,
                                     CriptografiaEmRepousoService criptografiaEmRepousoService,
                                     PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.criptografiaEmRepousoService = criptografiaEmRepousoService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Enfileirar e idempotente: a mesma (clientId, idempotencyKey) sempre retorna o id do mesmo job. */
    public Long enfileirar(String clientId, String idempotencyKey, NfePedidoEmissaoRequest pedido) {
        String pedidoJson = criptografiaEmRepousoService.criptografar(serializar(pedido));
        Instant agora = Instant.now();

        try {
            return transactionTemplate.execute(status -> {
                EmissaoAssincrona job = new EmissaoAssincrona(clientId, idempotencyKey, pedidoJson, agora);
                return repository.saveAndFlush(job).getId();
            });
        } catch (DataIntegrityViolationException colisao) {
            return repository.findByClientIdAndIdempotencyKey(clientId, idempotencyKey)
                    .orElseThrow(() -> colisao)
                    .getId();
        }
    }

    public Optional<EmissaoAssincronaStatusView> consultar(String clientId, Long id) {
        return repository.findByIdAndClientId(id, clientId).map(job -> new EmissaoAssincronaStatusView(
                job.getId(),
                job.getStatus(),
                job.getResultadoJson() != null ? descriptografar(job.getResultadoJson()) : null,
                job.getErroMensagem()));
    }

    private String serializar(NfePedidoEmissaoRequest pedido) {
        try {
            return objectMapper.writeValueAsString(pedido);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar pedido de emissao assincrona", e);
        }
    }

    private String descriptografar(String jsonCriptografado) {
        return criptografiaEmRepousoService.descriptografar(jsonCriptografado);
    }

    public record EmissaoAssincronaStatusView(Long id, StatusEmissaoAssincrona status, String resultadoJson, String erroMensagem) {
    }
}
