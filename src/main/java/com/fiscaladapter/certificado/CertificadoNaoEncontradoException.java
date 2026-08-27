package com.fiscaladapter.certificado;

public class CertificadoNaoEncontradoException extends RuntimeException {

    public CertificadoNaoEncontradoException(String cnpj) {
        super("Nenhum certificado registrado para o CNPJ " + cnpj + " - cadastre um antes de emitir documentos "
                + "para esse emissor (POST /api/v1/certificados)");
    }
}
