package com.fiscaladapter.sefaz.nfe;

/** dhRecbto (data/hora de autorizacao) e usado pelo NfeConsultaController para aplicar o prazo de cancelamento especifico da NFC-e (FIS-43). */
public record ConsultaProtocoloResponse(String codigoStatus, String motivo, String numeroProtocolo, String dhRecbto, boolean autorizada) {

    public static ConsultaProtocoloResponse de(String codigoStatus, String motivo, String numeroProtocolo, String dhRecbto) {
        // cStat 100 = "Autorizado o uso da NF-e"
        return new ConsultaProtocoloResponse(codigoStatus, motivo, numeroProtocolo, dhRecbto, "100".equals(codigoStatus));
    }
}
