package com.fiscaladapter.api.idempotencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.NfeResponse;
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
    private final TransactionTemplate transactionTemplate;

    public IdempotenciaService(RequisicaoIdempotenteRepository repository, ObjectMapper objectMapper,
                                PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public NfeResponse executar(String clientId, String chave, Supplier<NfeResponse> processamento) {
        ResultadoPlaceholder resultado = obterOuCriarPlaceholder(clientId, chave);

        if (resultado.respostaCache() != null) {
            return desserializar(resultado.respostaCache());
        }

        try {
            NfeResponse resposta = processamento.get();
            concluir(clientId, chave, resposta);
            return resposta;
        } catch (RuntimeException e) {
            removerPlaceholder(clientId, chave);
            throw e;
        }
    }

    private ResultadoPlaceholder obterOuCriarPlaceholder(String clientId, String chave) {
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                return transactionTemplate.execute(status -> {
                    Instant agora = Instant.now();
                    repository.save(new RequisicaoIdempotente(clientId, chave, agora, agora.plus(JANELA_VALIDADE)));
                    return new ResultadoPlaceholder(null);
                });
            } catch (DataIntegrityViolationException colisao) {
                RequisicaoIdempotente existente = repository.findByClientIdAndChave(clientId, chave)
                        .orElse(null);

                if (existente == null) {
                    continue; // foi removida entre a colisao e a leitura (falha concluida); tenta de novo
                }
                if (existente.getStatus() == StatusRequisicaoIdempotente.PROCESSANDO) {
                    throw new RequisicaoEmProcessamentoException(chave);
                }
                if (existente.expirada(Instant.now())) {
                    repository.deleteByClientIdAndChave(clientId, chave);
                    continue; // janela expirou, reprocessa como se fosse nova
                }
                return new ResultadoPlaceholder(existente.getRespostaJson());
            }
        }
        throw new IllegalStateException(
                "Nao foi possivel registrar a requisicao idempotente apos " + MAX_TENTATIVAS + " tentativas");
    }

    private void concluir(String clientId, String chave, NfeResponse resposta) {
        transactionTemplate.executeWithoutResult(status -> {
            RequisicaoIdempotente requisicao = repository.findByClientIdAndChave(clientId, chave).orElseThrow();
            requisicao.concluir(serializar(resposta));
        });
    }

    private void removerPlaceholder(String clientId, String chave) {
        transactionTemplate.executeWithoutResult(status -> repository.deleteByClientIdAndChave(clientId, chave));
    }

    private String serializar(NfeResponse resposta) {
        try {
            return objectMapper.writeValueAsString(resposta);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar resposta para cache de idempotencia", e);
        }
    }

    private NfeResponse desserializar(String json) {
        try {
            return objectMapper.readValue(json, NfeResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar resposta em cache de idempotencia", e);
        }
    }

    private record ResultadoPlaceholder(String respostaCache) {
    }
}
