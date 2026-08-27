package com.fiscaladapter.sefaz.nfe;

public record ConsultaProtocoloResponse(String codigoStatus, String motivo, String numeroProtocolo, boolean autorizada) {

    public static ConsultaProtocoloResponse de(String codigoStatus, String motivo, String numeroProtocolo) {
        // cStat 100 = "Autorizado o uso da NF-e"
        return new ConsultaProtocoloResponse(codigoStatus, motivo, numeroProtocolo, "100".equals(codigoStatus));
    }
}
