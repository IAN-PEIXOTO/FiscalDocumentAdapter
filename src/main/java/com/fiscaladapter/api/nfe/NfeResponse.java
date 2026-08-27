package com.fiscaladapter.api.nfe;

/** danfePdfBase64 vem nulo quando a nota foi rejeitada (nao ha DANFE valido para imprimir - ver FIS-8). */
public record NfeResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        boolean viaContingencia,
        boolean viaEpec,
        String danfePdfBase64
) {
}
