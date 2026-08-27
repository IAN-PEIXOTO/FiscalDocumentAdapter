package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazTransp (subconjunto: apenas modFrete por enquanto). */
public record TranspRequest(@NotNull Integer modFrete) {
}
