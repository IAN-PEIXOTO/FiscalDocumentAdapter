package com.fiscaladapter.api.nfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** mensagemErro/categoriaErro vem nulos quando cancelado=true (FIS-39). */
public record CancelamentoNfeResponse(
        String chaveAcesso,
        boolean cancelado,
        String codigoStatusSefaz,
        String motivoSefaz,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
