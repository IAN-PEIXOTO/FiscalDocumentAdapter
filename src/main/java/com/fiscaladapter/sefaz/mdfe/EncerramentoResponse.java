package com.fiscaladapter.sefaz.mdfe;

public record EncerramentoResponse(String codigoStatus, String motivo, boolean encerrado) {

    public static EncerramentoResponse de(String codigoStatus, String motivo) {
        // cStat 135 = "Evento registrado e vinculado ao MDF-e" (mesmo codigo generico de evento homologado usado por NFe/CT-e)
        return new EncerramentoResponse(codigoStatus, motivo, "135".equals(codigoStatus));
    }
}
