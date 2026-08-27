package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazICMS. Apenas ICMS00 suportado por enquanto (outros CST ficam para expansao futura). */
public record IcmsRequest(@NotNull @Valid Icms00Request ICMS00) {
}
