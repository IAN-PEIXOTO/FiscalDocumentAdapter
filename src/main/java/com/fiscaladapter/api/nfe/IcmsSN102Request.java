package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Espelha NfeSefazICMSSN102: emitente do Simples Nacional (CRT=1), CSOSN 102
 * (sem permissao de credito), 103 (isencao por faixa de receita), 300 (imune)
 * ou 400 (nao tributada) - sem valores, so orig+CSOSN.
 */
public record IcmsSN102Request(
        @NotBlank String orig,
        @NotBlank @Pattern(regexp = "102|103|300|400") String CSOSN
) {
}
