package com.fiscaladapter.certificado;

public class CertificadoInvalidoException extends RuntimeException {

    public CertificadoInvalidoException(String message) {
        super(message);
    }

    public CertificadoInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}
