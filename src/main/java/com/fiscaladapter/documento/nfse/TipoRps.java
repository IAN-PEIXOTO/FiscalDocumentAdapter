package com.fiscaladapter.documento.nfse;

/** tsTipoRps: tipo do Recibo Provisorio de Servicos. */
public enum TipoRps {
    RPS(1),
    NOTA_FISCAL_CONJUGADA_MISTA(2),
    CUPOM(3);

    private final int codigo;

    TipoRps(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }
}
