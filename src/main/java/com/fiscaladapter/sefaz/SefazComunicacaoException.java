package com.fiscaladapter.sefaz;

public class SefazComunicacaoException extends RuntimeException {

    public SefazComunicacaoException(String message, Throwable cause) {
        super(message, cause);
    }

    public SefazComunicacaoException(String message) {
        super(message);
    }
}
