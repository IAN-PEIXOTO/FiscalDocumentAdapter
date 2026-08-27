package com.fiscaladapter.seguranca;

public class EmissorNaoAutorizadoException extends RuntimeException {

    public EmissorNaoAutorizadoException(String cnpj) {
        super("Cliente nao autorizado a operar em nome do CNPJ " + cnpj);
    }
}
