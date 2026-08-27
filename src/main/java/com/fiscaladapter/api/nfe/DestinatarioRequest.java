package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DestinatarioRequest(
        @NotBlank String cpfOuCnpj,
        @NotBlank String razaoSocial,
        @NotBlank String indicadorInscricaoEstadual,
        String inscricaoEstadual,
        String email,
        @NotNull @Valid EnderecoRequest endereco
) {
}
