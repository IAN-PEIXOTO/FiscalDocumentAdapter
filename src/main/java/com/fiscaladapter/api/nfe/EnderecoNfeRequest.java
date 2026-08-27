package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;

/** Espelha NfeSefazEndereco/NfeSefazEnderEmi do schema da API ACBr. */
public record EnderecoNfeRequest(
        @NotBlank String xLgr,
        @NotBlank String nro,
        String xCpl,
        @NotBlank String xBairro,
        @NotBlank String cMun,
        @NotBlank String xMun,
        @NotBlank String UF,
        @NotBlank String CEP,
        String cPais,
        String xPais,
        String fone
) {
}
