package com.fiscaladapter.documento.nfe;

public enum TipoAmbiente {
    PRODUCAO(1),
    HOMOLOGACAO(2);

    private final int codigo;

    TipoAmbiente(int codigo) {
        this.codigo = codigo;
    }

    public int codigo() {
        return codigo;
    }
}
