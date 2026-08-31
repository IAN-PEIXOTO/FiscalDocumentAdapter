package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/**
 * mensagemErro/categoriaErro vem nulos quando autorizada=true (FIS-39, mesmo padrao do NfeResponse/CteResponse).
 * damdfePdfBase64 vem nulo quando o MDF-e foi rejeitado (mesma logica do NfeResponse/CteResponse - FIS-49).
 */
public record MdfeResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        String damdfePdfBase64,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
