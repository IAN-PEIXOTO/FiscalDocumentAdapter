package com.fiscaladapter.documento.nfe;

import java.math.BigDecimal;
import java.util.List;

public record NotaFiscalEletronica(
        IdentificacaoNfe identificacao,
        Emitente emitente,
        Destinatario destinatario,
        List<ItemNota> itens,
        List<DetalhePagamento> pagamentos,
        BigDecimal valorTroco
) {
    /** Sem troco (FIS-72) - mantido para nao quebrar os chamadores existentes que nao usam vTroco. */
    public NotaFiscalEletronica(IdentificacaoNfe identificacao, Emitente emitente, Destinatario destinatario,
                                 List<ItemNota> itens, List<DetalhePagamento> pagamentos) {
        this(identificacao, emitente, destinatario, itens, pagamentos, BigDecimal.ZERO);
    }

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

    public BigDecimal valorTotalPis() {
        return itens.stream().map(i -> i.imposto().valorPis()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal valorTotalCofins() {
        return itens.stream().map(i -> i.imposto().valorCofins()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal valorTotalNota() {
        return valorTotalProdutos().add(valorTotalIpi());
    }
}
