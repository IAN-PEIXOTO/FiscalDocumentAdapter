package com.fiscaladapter.api.idempotencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Garante que reenviar a mesma requisicao de emissao (mesmo client_id +
 * mesma chave de Idempotency-Key) nao processa duas vezes - essencial porque
 * reprocessar significaria consumir um numero de documento novo e possivelmente
 * emitir a mesma nota duas vezes na SEFAZ (FIS-38).
 */
@Service
public class IdempotenciaService {

    /** Janela de validade da chave de idempotencia: apos esse prazo, a mesma chave pode ser reutilizada. */
    static final Duration JANELA_VALIDADE = Duration.ofHours(24);

    private static final int MAX_TENTATIVAS = 3;

    private final RequisicaoIdempotenteRepository repository;
    private final ObjectMapper objectMapper;
    private final CriptografiaEmRepousoService criptografiaEmRepousoService;
    private final TransactionTemplate transactionTemplate;

    public IdempotenciaService(RequisicaoIdempotenteRepository repository, ObjectMapper objectMapper,
                                CriptografiaEmRepousoService criptografiaEmRepousoService,
                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.criptografiaEmRepousoService = criptografiaEmRepousoService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * tipoResposta e necessario para a desserializacao do cache (Jackson precisa saber o tipo
     * concreto) e tambem serve como discriminador da operacao (FIS-43): sem isso, a mesma
     * Idempotency-Key reusada entre endpoints diferentes (ex.: POST /api/v1/nfe e POST
     * /api/v1/nfce) colidiria na mesma chave (client_id, chave) e devolveria a resposta cacheada
     * do outro endpoint.
     */
    public <T> T executar(String clientId, String chave, Class<T> tipoResposta, Supplier<T> processamento) {
        String tipoOperacao = tipoResposta.getSimpleName();
        ResultadoPlaceholder resultado = obterOuCriarPlaceholder(clientId, tipoOperacao, chave);

        if (resultado.respostaCache() != null) {
            return desserializar(resultado.respostaCache(), tipoResposta);
        }

        try {
            T resposta = processamento.get();
            concluir(clientId, tipoOperacao, chave, resposta);
            return resposta;
        } catch (RuntimeException e) {
            removerPlaceholder(clientId, tipoOperacao, chave);
            throw e;
        }
    }

    private ResultadoPlaceholder obterOuCriarPlaceholder(String clientId, String tipoOperacao, String chave) {
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                return transactionTemplate.execute(status -> {
                    Instant agora = Instant.now();
                    repository.save(new RequisicaoIdempotente(clientId, tipoOperacao, chave, agora, agora.plus(JANELA_VALIDADE)));
                    return new ResultadoPlaceholder(null);
                });
            } catch (DataIntegrityViolationException colisao) {
                RequisicaoIdempotente existente = repository.findByClientIdAndTipoOperacaoAndChave(clientId, tipoOperacao, chave)
                        .orElse(null);

                if (existente == null) {
                    continue; // foi removida entre a colisao e a leitura (falha concluida); tenta de novo
                }
                if (existente.getStatus() == StatusRequisicaoIdempotente.PROCESSANDO) {
                    throw new RequisicaoEmProcessamentoException(chave);
                }
                if (existente.expirada(Instant.now())) {
                    repository.deleteByClientIdAndTipoOperacaoAndChave(clientId, tipoOperacao, chave);
                    continue; // janela expirou, reprocessa como se fosse nova
                }
                return new ResultadoPlaceholder(existente.getRespostaJson());
            }
        }
        throw new IllegalStateException(
                "Nao foi possivel registrar a requisicao idempotente apos " + MAX_TENTATIVAS + " tentativas");
    }

    private void concluir(String clientId, String tipoOperacao, String chave, Object resposta) {
        transactionTemplate.executeWithoutResult(status -> {
            RequisicaoIdempotente requisicao = repository.findByClientIdAndTipoOperacaoAndChave(clientId, tipoOperacao, chave)
                    .orElseThrow();
            requisicao.concluir(serializar(resposta));
        });
    }

    private void removerPlaceholder(String clientId, String tipoOperacao, String chave) {
        transactionTemplate.executeWithoutResult(status ->
                repository.deleteByClientIdAndTipoOperacaoAndChave(clientId, tipoOperacao, chave));
    }

    private String serializar(Object resposta) {
        try {
            String json = objectMapper.writeValueAsString(resposta);
            return criptografiaEmRepousoService.criptografar(json);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar resposta para cache de idempotencia", e);
        }
    }

    private <T> T desserializar(String respostaCriptografada, Class<T> tipoResposta) {
        try {
            String json = criptografiaEmRepousoService.descriptografar(respostaCriptografada);
            return objectMapper.readValue(json, tipoResposta);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar resposta em cache de idempotencia", e);
        }
    }

    private record ResultadoPlaceholder(String respostaCache) {
    }
}
