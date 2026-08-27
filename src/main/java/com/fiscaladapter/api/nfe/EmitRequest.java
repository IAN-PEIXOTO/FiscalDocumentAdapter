package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Espelha NfeSefazEmit do schema da API ACBr. */
public record EmitRequest(
        String CNPJ,
        String CPF,
        @NotBlank String xNome,
        String xFant,
        @NotNull @Valid EnderecoNfeRequest enderEmit,
        @NotBlank String IE,
        String IEST,
        String IM,
        String CNAE,
        @NotBlank String CRT
) {
}
