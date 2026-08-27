package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Espelha NfeSefazICMS40: isenta (40), nao tributada (41) ou suspensao (50) - sem valores, so orig+CST. */
public record Icms40Request(
        @NotBlank String orig,
        @NotBlank @Pattern(regexp = "40|41|50", message = "deve ser 40 (isenta), 41 (nao tributada) ou 50 (suspensao)") String CST
) {
}
