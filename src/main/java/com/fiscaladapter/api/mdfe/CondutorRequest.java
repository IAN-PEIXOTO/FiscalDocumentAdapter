package com.fiscaladapter.api.mdfe;

import jakarta.validation.constraints.NotBlank;

public record CondutorRequest(@NotBlank String xNome, @NotBlank String CPF) {
}
