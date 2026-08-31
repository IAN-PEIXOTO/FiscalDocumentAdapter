package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Emitente do MDF-e - sem CRT, diferente do emitente de NFe/CT-e (ver EmitenteMdfe). */
public record EmitMdfeRequest(
        @NotBlank String CNPJ,
        @NotBlank String xNome,
        String xFant,
        String IE,
        @NotNull @Valid EnderecoNfeRequest enderEmit
) {
}
