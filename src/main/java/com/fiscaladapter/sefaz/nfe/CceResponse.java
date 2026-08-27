package com.fiscaladapter.sefaz.nfe;

public record CceResponse(String codigoStatus, String motivo, String numeroProtocolo, boolean registrada) {

    public static CceResponse de(String codigoStatus, String motivo, String numeroProtocolo) {
        // cStat 135 = "Evento registrado e vinculado a NF-e" (mesmo codigo generico usado por qualquer evento homologado)
        return new CceResponse(codigoStatus, motivo, numeroProtocolo, "135".equals(codigoStatus));
    }
}
