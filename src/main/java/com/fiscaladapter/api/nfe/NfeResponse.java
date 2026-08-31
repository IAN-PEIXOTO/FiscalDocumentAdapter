package com.fiscaladapter.api.nfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/**
 * danfePdfBase64 vem nulo quando a nota foi rejeitada (nao ha DANFE valido para imprimir - ver FIS-8).
 * mensagemErro/categoriaErro vem nulos quando autorizada=true ou viaEpec=true (nao ha erro para
 * explicar) - preenchidos a partir do catalogo de rejeicoes da SEFAZ (FIS-39) quando a nota foi
 * de fato rejeitada. codigoStatusSefaz/motivoSefaz continuam sempre presentes (fallback, mesmo
 * para codigos fora do catalogo).
 */
public record NfeResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        boolean viaContingencia,
        boolean viaEpec,
        String danfePdfBase64,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
