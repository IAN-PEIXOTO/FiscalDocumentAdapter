package com.fiscaladapter.documento.nfse;

import java.math.BigDecimal;

/**
 * tcValoresDeclaracaoServico. Apenas ValorServicos e obrigatorio no XSD; os
 * demais (deducoes, retencoes de PIS/COFINS/INSS/IR/CSLL, descontos) sao
 * opcionais e ficam null quando nao informados - a tag correspondente
 * simplesmente nao e escrita no XML.
 */
public record ValoresServicoNfse(
        BigDecimal valorServicos,
        BigDecimal valorDeducoes,
        BigDecimal valorIss,
        BigDecimal aliquota
) {
}
