package com.fiscaladapter.api.cte;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record IdeCteRequest(
        @NotBlank String uf,
        @NotBlank String cfop,
        @NotBlank String natOp,
        @NotNull @Positive Integer serie,
        @NotNull @Positive Long nCT,
        @NotNull LocalDate dhEmi,
        @NotBlank String cMunEnv,
        @NotBlank String xMunEnv,
        @NotBlank String UFEnv,
        @NotBlank String cMunFim,
        @NotBlank String xMunFim,
        @NotBlank String UFFim
) {
}
