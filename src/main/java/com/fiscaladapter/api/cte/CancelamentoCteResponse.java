package com.fiscaladapter.api.cte;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** mensagemErro/categoriaErro vem nulos quando cancelado=true (FIS-39). */
public record CancelamentoCteResponse(
        String chaveAcesso,
        boolean cancelado,
        String codigoStatusSefaz,
        String motivoSefaz,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
