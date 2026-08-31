package com.fiscaladapter.api.cte;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** imp/ICMS/ICMS00 - prestacao sujeita a tributacao normal do ICMS (CST fixo "00", ver CteXmlGenerator). */
public record ImpostoCteRequest(
        @NotNull @PositiveOrZero BigDecimal vBC,
        @NotNull @PositiveOrZero BigDecimal pICMS,
        @NotNull @PositiveOrZero BigDecimal vICMS
) {
}
