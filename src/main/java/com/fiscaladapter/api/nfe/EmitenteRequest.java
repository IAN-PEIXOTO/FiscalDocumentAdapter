package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EmitenteRequest(
        @NotBlank String cnpj,
        @NotBlank String razaoSocial,
        String nomeFantasia,
        @NotBlank String inscricaoEstadual,
        @NotBlank String regimeTributario,
        @NotNull @Valid EnderecoRequest endereco
) {
}
