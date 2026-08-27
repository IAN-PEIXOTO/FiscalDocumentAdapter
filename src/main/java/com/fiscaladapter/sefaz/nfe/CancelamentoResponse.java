package com.fiscaladapter.sefaz.nfe;

public record CancelamentoResponse(String codigoStatus, String motivo, boolean cancelado) {

    public static CancelamentoResponse de(String codigoStatus, String motivo) {
        // cStat 135 = "Evento registrado e vinculado a NF-e" (cancelamento homologado)
        return new CancelamentoResponse(codigoStatus, motivo, "135".equals(codigoStatus));
    }
}
