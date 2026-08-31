package com.fiscaladapter.api.cte;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Mesmo estilo do NfePedidoEmissaoRequest, mas para CT-e (modelo 57, FIS-44) - schema proprio, nao compartilhado com a NFe. */
public record CtePedidoEmissaoRequest(
        @NotBlank @Pattern(regexp = "homologacao|producao") String ambiente,
        String referencia,
        @NotNull @Valid InfCteRequest infCte
) {
}
