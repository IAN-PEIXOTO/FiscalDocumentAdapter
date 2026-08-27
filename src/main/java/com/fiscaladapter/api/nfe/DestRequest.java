package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazDest do schema da API ACBr. */
public record DestRequest(
        String CNPJ,
        String CPF,
        String idEstrangeiro,
        @NotBlank String xNome,
        @NotNull @Valid EnderecoNfeRequest enderDest,
        @NotNull Integer indIEDest,
        String IE,
        String ISUF,
        String IM,
        String email
) {
}
