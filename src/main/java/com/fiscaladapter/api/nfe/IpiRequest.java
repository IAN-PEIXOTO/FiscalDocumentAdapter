package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazIpi. Campo opcional em ImpostoRequest: nem todo item tem IPI. */
public record IpiRequest(
        @NotBlank String cEnq,
        @NotNull @Valid IpiTribRequest IPITrib
) {
}
