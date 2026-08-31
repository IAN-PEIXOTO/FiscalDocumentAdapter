package com.fiscaladapter.sefaz.nfe;

/** cSitNFe do resumo (resNFe) da NFeDistribuicaoDFe (FIS-40). */
public enum SituacaoNfeDistribuicao {
    AUTORIZADA("1"),
    CANCELADA("2"),
    DENEGADA("3"),
    DESCONHECIDA(null);

    private final String codigo;

    SituacaoNfeDistribuicao(String codigo) {
        this.codigo = codigo;
    }

    public static SituacaoNfeDistribuicao de(String codigo) {
        for (SituacaoNfeDistribuicao situacao : values()) {
            if (situacao.codigo != null && situacao.codigo.equals(codigo)) {
                return situacao;
            }
        }
        return DESCONHECIDA;
    }
}
