package com.fiscaladapter.api.idempotencia;

public class RequisicaoEmProcessamentoException extends RuntimeException {

    public RequisicaoEmProcessamentoException(String chave) {
        super("Ja existe uma requisicao em processamento para a chave de idempotencia '" + chave
                + "'. Aguarde a conclusao antes de tentar novamente.");
    }
}
