package com.fiscaladapter.sefaz.nfe;

/**
 * Sefaz Virtual de Contingencia - usada quando o webservice proprio da UF do
 * emitente esta indisponivel (FIS-37). Os codigos tpEmis vem do XSD oficial
 * (leiauteNFe_v4.00.xsd): 6=SVC-AN, 7=SVC-RS.
 */
public enum ServicoContingenciaSvc {
    SVC_AN("SVC-AN", "6"),
    SVC_RS("SVC-RS", "7");

    private final String chaveEndpoint;
    private final String tpEmis;

    ServicoContingenciaSvc(String chaveEndpoint, String tpEmis) {
        this.chaveEndpoint = chaveEndpoint;
        this.tpEmis = tpEmis;
    }

    /** Chave usada em SefazEndpointRegistry (a SVC e cadastrada como se fosse uma "UF" propria). */
    public String chaveEndpoint() {
        return chaveEndpoint;
    }

    public String tpEmis() {
        return tpEmis;
    }
}
