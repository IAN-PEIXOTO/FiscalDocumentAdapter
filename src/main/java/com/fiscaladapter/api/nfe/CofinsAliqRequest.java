package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CofinsAliqRequest(
        @NotBlank String CST,
        @NotNull @PositiveOrZero BigDecimal vBC,
        @NotNull @PositiveOrZero BigDecimal pCOFINS,
        @NotNull @PositiveOrZero BigDecimal vCOFINS
) {
}
