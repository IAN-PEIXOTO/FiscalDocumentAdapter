package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazPIS. Apenas PISAliq suportado por enquanto. */
public record PisRequest(@NotNull @Valid PisAliqRequest PISAliq) {
}
