package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Espelha NfeSefazDetPag (subconjunto: campos mais comuns). */
public record DetPagRequest(
        @NotBlank String tPag,
        @NotNull @Positive BigDecimal vPag
) {
}
