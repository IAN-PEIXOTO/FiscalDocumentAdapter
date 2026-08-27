package com.fiscaladapter.documento.nfe.rvn;

import java.util.List;

public class RegraNegocioVioladaException extends RuntimeException {

    private final List<ViolacaoRegra> violacoes;

    public RegraNegocioVioladaException(List<ViolacaoRegra> violacoes) {
        super("Documento viola regras de negocio: " + violacoes);
        this.violacoes = violacoes;
    }

    public List<ViolacaoRegra> getViolacoes() {
        return violacoes;
    }
}
