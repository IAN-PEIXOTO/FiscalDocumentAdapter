package com.fiscaladapter.documento.cte;

import java.math.BigDecimal;

/** infCarga: dados da carga transportada. infQ e fixado em peso bruto (cUnid=01 KG), o caso mais comum. */
public record InformacaoCarga(
        BigDecimal valorCarga,
        String produtoPredominante,
        BigDecimal pesoBrutoKg
) {
}
