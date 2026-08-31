package com.fiscaladapter.api.cte;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

import java.util.List;

/**
 * mensagemErro/categoriaErro vem nulos quando autorizada=true (FIS-39, mesmo padrao do NfeResponse).
 * notasFiscaisTransportadas (FIS-44, criterio de aceite 3) - as chaves de NF-e vinculadas a este
 * CT-e, ecoadas de volta do proprio pedido.
 * dactePdfBase64 vem nulo quando o CT-e foi rejeitado (mesma logica do NfeResponse - FIS-48).
 */
public record CteResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        List<String> notasFiscaisTransportadas,
        String dactePdfBase64,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
