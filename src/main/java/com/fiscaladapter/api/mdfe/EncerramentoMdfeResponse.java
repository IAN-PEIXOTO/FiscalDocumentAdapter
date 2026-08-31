package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** Encerramento = fim de percurso do MDF-e (FIS-45, criterio de aceite 2). */
public record EncerramentoMdfeResponse(
        String chaveAcesso,
        boolean encerrado,
        String codigoStatusSefaz,
        String motivoSefaz,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
