package com.fiscaladapter.documento.cte;

/** Papel do tomador do servico de transporte (grupo toma3 do XSD - ide/toma3/toma). */
public enum TipoTomadorServico {
    REMETENTE("0"),
    EXPEDIDOR("1"),
    RECEBEDOR("2"),
    DESTINATARIO("3");

    private final String codigo;

    TipoTomadorServico(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
