package com.fiscaladapter.api.nfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** mensagemErro/categoriaErro vem nulos quando autorizada=true (FIS-39). */
public record ConsultaNfeResponse(
        String chaveAcesso,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
