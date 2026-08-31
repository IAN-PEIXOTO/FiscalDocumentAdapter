package com.fiscaladapter.api.mdfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record IdeMdfeRequest(
        @NotBlank String uf,
        @NotBlank String UFIni,
        @NotBlank String UFFim,
        @NotNull @Positive Integer serie,
        @NotNull @Positive Long nMDF,
        @NotNull LocalDate dhEmi,
        @NotBlank String cMunCarrega,
        @NotBlank String xMunCarrega
) {
}
