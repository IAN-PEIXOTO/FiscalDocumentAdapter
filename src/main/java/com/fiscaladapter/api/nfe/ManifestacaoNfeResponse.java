package com.fiscaladapter.api.nfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/** mensagemErro/categoriaErro vem nulos quando registrada=true (FIS-39). */
public record ManifestacaoNfeResponse(String chaveAcesso, boolean registrada, String codigoStatusSefaz,
                                       String motivo, String numeroProtocolo, String mensagemErro,
                                       CategoriaErroSefaz categoriaErro) {
}
