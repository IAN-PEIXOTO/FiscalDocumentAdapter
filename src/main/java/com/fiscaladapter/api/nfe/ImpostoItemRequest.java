package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record ImpostoItemRequest(
        @NotBlank String origemIcms,
        @NotBlank String cstIcms,
        @NotNull @PositiveOrZero BigDecimal baseCalculoIcms,
        @NotNull @PositiveOrZero BigDecimal aliquotaIcms,
        @NotNull @PositiveOrZero BigDecimal valorIcms,
        @NotNull @PositiveOrZero BigDecimal valorIpi,
        @NotNull @PositiveOrZero BigDecimal valorPis,
        @NotNull @PositiveOrZero BigDecimal valorCofins
) {
}
