package com.fiscaladapter.api.mdfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public record InfMdfeRequest(
        @NotNull @Valid IdeMdfeRequest ide,
        @NotNull @Valid EmitMdfeRequest emit,
        String rntrc,
        @NotNull @Valid VeiculoTracaoRequest veicTracao,
        @NotEmpty List<@Valid CondutorRequest> condutores,
        @NotBlank String cMunDescarga,
        @NotBlank String xMunDescarga,
        List<String> infCte,
        List<String> infNFe,
        @NotNull @PositiveOrZero BigDecimal vCarga,
        @NotNull @PositiveOrZero BigDecimal pesoBrutoKg
) {
}
