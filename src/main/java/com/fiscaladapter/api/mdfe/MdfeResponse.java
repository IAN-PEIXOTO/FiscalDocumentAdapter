package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** mensagemErro/categoriaErro vem nulos quando autorizada=true (FIS-39, mesmo padrao do NfeResponse/CteResponse). */
public record MdfeResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
