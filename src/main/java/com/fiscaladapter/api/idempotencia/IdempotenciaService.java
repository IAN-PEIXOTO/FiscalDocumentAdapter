package com.fiscaladapter.api.idempotencia;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final Logger log = LoggerFactory.getLogger(IdempotenciaService.class);

    /** Janela de validade do cache de resposta: apos esse prazo, a mesma chave pode ser reutilizada. */
    static final Duration JANELA_VALIDADE = Duration.ofHours(24);

    /**
     * Tempo maximo que uma requisicao fica legitimamente em PROCESSANDO (FIS-65) - bem maior que
     * o pior caso de latencia normal (endpoint normal + contingencia + EPEC, ver
     * EmissaoNfeOrquestrador, na casa de poucos minutos), mas MUITO menor que a janela de 24h do
     * cache de resposta. Sem esse limite, uma requisicao presa em PROCESSANDO (processo caiu
     * entre transmitir a SEFAZ e gravar a resposta) bloquearia a mesma Idempotency-Key com 409
     * para sempre, ja que o status PROCESSANDO e checado antes da janela de 24h.
     */
    static final Duration TEMPO_LIMITE_PROCESSANDO = Duration.ofMinutes(10);

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
                    if (existente.processandoHaMuitoTempo(Instant.now(), TEMPO_LIMITE_PROCESSANDO)) {
                        log.warn("Requisicao idempotente presa em PROCESSANDO ha mais de {} - processo provavelmente "
                                        + "caiu antes de concluir; liberando para reprocessamento (clientId={}, "
                                        + "tipoOperacao={}, chave={})",
                                TEMPO_LIMITE_PROCESSANDO, clientId, tipoOperacao, chave);
                        repository.deleteByClientIdAndTipoOperacaoAndChave(clientId, tipoOperacao, chave);
                        continue;
                    }
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

    /**
     * Expurgo periodico das linhas expiradas (FIS-81) - roda a cada hora, independente do trafego
     * de reenvios. Sem isso, a tabela cresce proporcionalmente ao volume historico total de
     * operacoes ja processadas (o CLOB de resposta cifrada incluido), nao ao volume dentro da
     * janela de 24h.
     */
    @Scheduled(fixedRateString = "${fiscaladapter.idempotencia.intervalo-expurgo-ms:3600000}")
    public void expurgarExpiradas() {
        Integer removidas = transactionTemplate.execute(status -> repository.expurgarExpiradasAntesDe(Instant.now()));
        if (removidas != null && removidas > 0) {
            log.info("Expurgadas {} requisicoes idempotentes expiradas", removidas);
        }
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
