package com.fiscaladapter.api.nfe;

public record NfeResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        boolean viaContingencia,
        boolean viaEpec
) {
}
