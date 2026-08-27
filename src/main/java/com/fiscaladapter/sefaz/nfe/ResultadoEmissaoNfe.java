package com.fiscaladapter.sefaz.nfe;

/**
 * viaEpec=true significa que nem o endpoint normal nem a SVC de contingencia
 * responderam, e a NFe foi apenas provisoriamente liberada via evento EPEC
 * (autorizacao.autorizada() sera false nesse caso - a autorizacao definitiva
 * so ocorre quando a NFe completa puder ser transmitida normalmente, o que
 * exige retomada assincrona, fora do escopo atual - ver FIS-30).
 */
public record ResultadoEmissaoNfe(
        String chaveAcesso,
        String xmlAssinado,
        AutorizacaoResponse autorizacao,
        boolean viaContingencia,
        boolean viaEpec
) {
}
