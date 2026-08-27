package com.fiscaladapter.api.nfe;

public record ManifestacaoNfeResponse(String chaveAcesso, boolean registrada, String codigoStatusSefaz,
                                       String motivo, String numeroProtocolo) {
}
