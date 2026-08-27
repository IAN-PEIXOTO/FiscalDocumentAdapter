package com.fiscaladapter.documento.nfse;

/**
 * tcDadosServico. exigibilidadeIss segue a tabela do XSD: 1-Exigivel;
 * 2-Nao incidencia; 3-Isencao; 4-Exportacao; 5-Imunidade;
 * 6-Exigibilidade suspensa por decisao judicial; 7-idem por decisao
 * administrativa.
 */
public record DadosServicoNfse(
        ValoresServicoNfse valores,
        boolean issRetido,
        String itemListaServico,
        String discriminacao,
        String codigoMunicipioPrestacao,
        int exigibilidadeIss
) {
}
