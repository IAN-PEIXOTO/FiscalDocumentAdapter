package com.fiscaladapter.api.cte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record InformacaoCargaRequest(
        @NotNull @PositiveOrZero BigDecimal vCarga,
        @NotBlank String proPred,
        @NotNull @PositiveOrZero BigDecimal pesoBrutoKg
) {
}
