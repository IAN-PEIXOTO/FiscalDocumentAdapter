package com.fiscaladapter.api.nfe;

public record InutilizacaoNfeResponse(boolean inutilizada, String codigoStatusSefaz, String motivo, String numeroProtocolo) {
}
