package com.fiscaladapter.documento.nfe;

import java.math.BigDecimal;

/**
 * Subconjunto dos tributos por item exigidos pelo layout da NFe.
 * origem/cst seguem as tabelas oficiais do ICMS (ex.: origem 0 = nacional, cst 00 = tributada integralmente).
 */
public record ImpostoItem(
        String origemIcms,
        String cstIcms,
        BigDecimal baseCalculoIcms,
        BigDecimal aliquotaIcms,
        BigDecimal valorIcms,
        BigDecimal valorIpi,
        BigDecimal valorPis,
        BigDecimal valorCofins
) {
    public BigDecimal valorTotalTributos() {
        return valorIcms.add(valorIpi).add(valorPis).add(valorCofins);
    }
}
