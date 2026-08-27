package com.fiscaladapter.sefaz.nfe;

/**
 * Eventos de Manifestacao do Destinatario (FIS-9/FIS-40). descEvento e exigeJustificativa
 * verificados contra o XSD oficial de cada evento (e210200/e210210/e210220/e210240
 * v1.00) - texto sem acentos mesmo, e' o valor exato do enum do schema, nao uma
 * transliteracao nossa.
 */
public enum TipoManifestacaoDestinatario {
    CONFIRMACAO_DA_OPERACAO("210200", "Confirmacao da Operacao", false),
    CIENCIA_DA_OPERACAO("210210", "Ciencia da Operacao", false),
    DESCONHECIMENTO_DA_OPERACAO("210220", "Desconhecimento da Operacao", false),
    OPERACAO_NAO_REALIZADA("210240", "Operacao nao Realizada", true);

    private final String tpEvento;
    private final String descEvento;
    private final boolean exigeJustificativa;

    TipoManifestacaoDestinatario(String tpEvento, String descEvento, boolean exigeJustificativa) {
        this.tpEvento = tpEvento;
        this.descEvento = descEvento;
        this.exigeJustificativa = exigeJustificativa;
    }

    public String tpEvento() {
        return tpEvento;
    }

    public String descEvento() {
        return descEvento;
    }

    public boolean exigeJustificativa() {
        return exigeJustificativa;
    }
}
