package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazCOFINS. Apenas COFINSAliq suportado por enquanto. */
public record CofinsRequest(@NotNull @Valid CofinsAliqRequest COFINSAliq) {
}
