package com.fiscaladapter.documento.nfe;

import java.math.BigDecimal;
import java.util.List;

public record NotaFiscalEletronica(
        IdentificacaoNfe identificacao,
        Emitente emitente,
        Destinatario destinatario,
        List<ItemNota> itens,
        List<DetalhePagamento> pagamentos
) {
    public BigDecimal valorTotalProdutos() {
        return itens.stream().map(ItemNota::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal valorTotalTributos() {
        return itens.stream().map(i -> i.imposto().valorTotalTributos()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal valorTotalIcms() {
        return itens.stream().map(i -> i.imposto().valorIcms()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal valorTotalIpi() {
        return itens.stream().map(i -> i.imposto().valorIpi()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal valorTotalNota() {
        return valorTotalProdutos().add(valorTotalIpi());
    }
}
