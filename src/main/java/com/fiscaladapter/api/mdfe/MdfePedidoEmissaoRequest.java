package com.fiscaladapter.api.mdfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Mesmo estilo do CtePedidoEmissaoRequest, mas para MDF-e (modelo 58, FIS-45) - schema proprio. */
public record MdfePedidoEmissaoRequest(
        @NotBlank @Pattern(regexp = "homologacao|producao") String ambiente,
        String referencia,
        @NotNull @Valid InfMdfeRequest infMDFe
) {
}
