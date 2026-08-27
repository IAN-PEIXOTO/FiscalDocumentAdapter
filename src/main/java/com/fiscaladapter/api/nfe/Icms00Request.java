package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Espelha NfeSefazICMS00. Suportamos apenas CST 00 nesta fase (ver ImpostoRequest). */
public record Icms00Request(
        @NotBlank String orig,
        @NotBlank String CST,
        @NotNull Integer modBC,
        @NotNull @PositiveOrZero BigDecimal vBC,
        @NotNull @PositiveOrZero BigDecimal pICMS,
        @NotNull @PositiveOrZero BigDecimal vICMS
) {
}
