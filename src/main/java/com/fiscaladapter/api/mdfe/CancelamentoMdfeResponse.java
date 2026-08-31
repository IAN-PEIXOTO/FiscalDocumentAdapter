package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

public record CancelamentoMdfeResponse(
        String chaveAcesso,
        boolean cancelado,
        String codigoStatusSefaz,
        String motivoSefaz,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
