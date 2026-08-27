package com.fiscaladapter.documento.nfe;

import java.math.BigDecimal;

public record ItemNota(
        int numero,
        String codigoProduto,
        String descricao,
        String ncm,
        String cfop,
        String unidadeComercial,
        BigDecimal quantidade,
        BigDecimal valorUnitario,
        BigDecimal valorTotal,
        ImpostoItem imposto
) {
}
