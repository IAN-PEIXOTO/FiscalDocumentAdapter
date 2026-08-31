package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

public record ConsultaMdfeResponse(
        String chaveAcesso,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
