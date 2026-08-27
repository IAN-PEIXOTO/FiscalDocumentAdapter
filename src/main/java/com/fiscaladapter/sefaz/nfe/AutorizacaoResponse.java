package com.fiscaladapter.sefaz.nfe;

public record AutorizacaoResponse(String codigoStatus, String motivo, String numeroProtocolo, boolean autorizada) {

    public static AutorizacaoResponse de(String codigoStatus, String motivo, String numeroProtocolo) {
        // cStat 100 = "Autorizado o uso da NF-e" (no lote sincrono, esse cStat vem no protNFe, nao no retorno do lote)
        return new AutorizacaoResponse(codigoStatus, motivo, numeroProtocolo, "100".equals(codigoStatus));
    }
}
