package com.fiscaladapter.api.mdfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record VeiculoTracaoRequest(
        @NotBlank String placa,
        @NotNull @Positive BigDecimal tara,
        @NotBlank String tpRod,
        @NotBlank String tpCar,
        String UF
) {
}
