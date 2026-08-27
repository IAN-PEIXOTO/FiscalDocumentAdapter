package com.fiscaladapter.sefaz.nfe;

public record InutilizacaoResponse(String codigoStatus, String motivo, String numeroProtocolo, boolean inutilizada) {

    public static InutilizacaoResponse de(String codigoStatus, String motivo, String numeroProtocolo) {
        // cStat 102 = "Inutilizacao de numero homologada"
        return new InutilizacaoResponse(codigoStatus, motivo, numeroProtocolo, "102".equals(codigoStatus));
    }
}
