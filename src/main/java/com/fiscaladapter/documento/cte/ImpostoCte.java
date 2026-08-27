package com.fiscaladapter.documento.cte;

import java.math.BigDecimal;

/** Imposto do CT-e (imp/ICMS/ICMS00 - prestacao sujeita a tributacao normal do ICMS, CST fixo "00"). */
public record ImpostoCte(
        BigDecimal baseCalculoIcms,
        BigDecimal aliquotaIcms,
        BigDecimal valorIcms
) {
}
