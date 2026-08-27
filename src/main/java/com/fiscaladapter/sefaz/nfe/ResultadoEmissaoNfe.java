package com.fiscaladapter.sefaz.nfe;

public record ResultadoEmissaoNfe(
        String chaveAcesso,
        String xmlAssinado,
        AutorizacaoResponse autorizacao,
        boolean viaContingencia
) {
}
