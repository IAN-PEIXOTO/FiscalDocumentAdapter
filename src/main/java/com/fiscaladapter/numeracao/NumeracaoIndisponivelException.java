package com.fiscaladapter.numeracao;

/** O numero de documento informado pelo cliente ja foi utilizado para aquele emissor/UF/serie/tipo de documento. */
public class NumeracaoIndisponivelException extends RuntimeException {

    public NumeracaoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
