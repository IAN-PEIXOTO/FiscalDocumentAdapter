package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;

/** Espelha o grupo infIntermed - so obrigatorio quando ide.indIntermed() == 1 (ver RVN-007). */
public record IntermediadorRequest(@NotBlank String CNPJ, @NotBlank String idCadIntTran) {
}
