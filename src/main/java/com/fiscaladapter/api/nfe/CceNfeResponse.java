package com.fiscaladapter.api.nfe;

public record CceNfeResponse(
        String chaveAcesso,
        boolean registrada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo
) {
}
