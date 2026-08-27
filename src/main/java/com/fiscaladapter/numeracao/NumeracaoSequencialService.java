package com.fiscaladapter.numeracao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Garante numeracao sequencial atomica por emissor/UF/serie/tipo de documento,
 * mesmo sob requisicoes concorrentes (FIS-28). Pular ou duplicar numero e uma
 * violacao legal, entao a garantia de atomicidade e mais importante que performance aqui.
 */
@Service
public class NumeracaoSequencialService {

    private static final int MAX_TENTATIVAS = 3;

    private final SequenciaDocumentoRepository repository;
    private final TransactionTemplate transactionTemplate;

    public NumeracaoSequencialService(SequenciaDocumentoRepository repository,
                                       PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public long proximoNumero(String cnpjEmissor, String uf, int serie, TipoDocumentoFiscal tipoDocumento) {
        String cnpjNormalizado = normalizarCnpj(cnpjEmissor);
        String ufNormalizada = normalizarUf(uf);

        DataIntegrityViolationException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                return transactionTemplate.execute(status -> {
                    SequenciaDocumento sequencia = repository
                            .buscarParaAtualizar(cnpjNormalizado, ufNormalizada, serie, tipoDocumento)
                            .orElseGet(() -> repository.save(
                                    new SequenciaDocumento(cnpjNormalizado, ufNormalizada, serie, tipoDocumento)));
                    return sequencia.incrementarEObterProximo();
                });
            } catch (DataIntegrityViolationException e) {
                // outra requisicao concorrente criou a mesma sequencia primeiro (colisao na constraint unica);
                // a proxima tentativa vai encontra-la via buscarParaAtualizar e incrementar normalmente
                ultimaFalha = e;
            }
        }
        throw new NumeracaoIndisponivelException(
                "Nao foi possivel obter numero sequencial apos " + MAX_TENTATIVAS + " tentativas", ultimaFalha);
    }

    private String normalizarCnpj(String cnpj) {
        String digits = cnpj.replaceAll("\\D", "");
        if (digits.length() != 14) {
            throw new IllegalArgumentException("CNPJ invalido: " + cnpj);
        }
        return digits;
    }

    private String normalizarUf(String uf) {
        String normalizada = uf.trim().toUpperCase();
        if (normalizada.length() != 2) {
            throw new IllegalArgumentException("UF invalida: " + uf);
        }
        return normalizada;
    }
}
