package com.fiscaladapter.api.nfe;

public record ConsultaNfeResponse(
        String chaveAcesso,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo
) {
}
