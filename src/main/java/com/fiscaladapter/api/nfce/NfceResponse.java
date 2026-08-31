package com.fiscaladapter.api.nfce;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/**
 * mensagemErro/categoriaErro vem nulos quando autorizada=true (FIS-39, mesmo padrao do NfeResponse).
 * conteudoQrCode/urlConsultaPublica sao devolvidos mesmo quando rejeitada (o cupom so deveria ser
 * impresso se autorizada=true, mas o cliente pode querer o conteudo para depuracao).
 * danfePdfBase64 vem nulo quando a nota foi rejeitada (mesma logica do NfeResponse - FIS-47).
 */
public record NfceResponse(
        String chaveAcesso,
        String xmlAssinado,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        String conteudoQrCode,
        String urlConsultaPublica,
        String danfePdfBase64,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
