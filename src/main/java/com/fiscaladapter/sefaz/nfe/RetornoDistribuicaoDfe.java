package com.fiscaladapter.sefaz.nfe;

import java.util.List;

/**
 * Resultado bruto de uma chamada a NFeDistribuicaoDFe (FIS-40). cStat 137
 * ("Nenhum documento localizado") e 138 ("Documento localizado") sao os
 * unicos considerados sucesso - qualquer outro (ex.: 656 "Consumo Indevido",
 * quando consultado com frequencia excessiva) e um erro que o chamador deve
 * tratar.
 */
public record RetornoDistribuicaoDfe(String cStat, String xMotivo, String ultNsu, String maxNsu,
                                      List<ResumoNfeDistribuicao> resumos) {

    private static final String NENHUM_DOCUMENTO = "137";
    private static final String DOCUMENTO_LOCALIZADO = "138";

    public boolean sucesso() {
        return NENHUM_DOCUMENTO.equals(cStat) || DOCUMENTO_LOCALIZADO.equals(cStat);
    }
}
