package com.fiscaladapter.api.nfe;

public record CancelamentoNfeResponse(
        String chaveAcesso,
        boolean cancelado,
        String codigoStatusSefaz,
        String motivoSefaz
) {
}
