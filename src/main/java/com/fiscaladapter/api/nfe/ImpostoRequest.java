package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazImposto. IPI e opcional (nem todo item tem). */
public record ImpostoRequest(
        @NotNull @Valid IcmsRequest ICMS,
        @Valid IpiRequest IPI,
        @NotNull @Valid PisRequest PIS,
        @NotNull @Valid CofinsRequest COFINS
) {
}
