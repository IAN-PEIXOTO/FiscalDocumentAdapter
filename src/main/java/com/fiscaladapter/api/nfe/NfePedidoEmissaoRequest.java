package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Espelha NfePedidoEmissao da API ACBr (https://dev.acbr.api.br/docs/api),
 * para permitir que sistemas ja integrados com a API paga do ACBr troquem
 * apenas a URL de destino sem reescrever a integracao.
 */
public record NfePedidoEmissaoRequest(
        @NotBlank @Pattern(regexp = "homologacao|producao") String ambiente,
        String referencia,
        @NotNull @Valid InfNfeRequest infNFe
) {
}
