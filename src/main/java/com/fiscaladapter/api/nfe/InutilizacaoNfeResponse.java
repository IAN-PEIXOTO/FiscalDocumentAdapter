package com.fiscaladapter.api.nfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** mensagemErro/categoriaErro vem nulos quando inutilizada=true (FIS-39). */
public record InutilizacaoNfeResponse(boolean inutilizada, String codigoStatusSefaz, String motivo,
                                       String numeroProtocolo, String mensagemErro, CategoriaErroSefaz categoriaErro) {
}
