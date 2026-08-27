package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/** Espelha NfeSefazProd do schema da API ACBr (subconjunto: os campos obrigatorios). */
public record ProdRequest(
        @NotBlank String cProd,
        @NotBlank String cEAN,
        @NotBlank String xProd,
        @NotBlank String NCM,
        @NotBlank String CFOP,
        @NotBlank String uCom,
        @NotNull @Positive BigDecimal qCom,
        @NotNull @Positive BigDecimal vUnCom,
        @NotNull @Positive BigDecimal vProd,
        @NotBlank String cEANTrib,
        @NotBlank String uTrib,
        @NotNull @Positive BigDecimal qTrib,
        @NotNull @Positive BigDecimal vUnTrib,
        @NotNull Integer indTot
) {
}
