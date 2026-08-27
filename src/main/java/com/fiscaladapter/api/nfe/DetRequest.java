package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Espelha NfeSefazDet. */
public record DetRequest(
        @NotNull @Positive Integer nItem,
        @NotNull @Valid ProdRequest prod,
        @NotNull @Valid ImpostoRequest imposto
) {
}
