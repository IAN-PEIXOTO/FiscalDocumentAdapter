package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ItemRequest(
        @NotNull @Positive Integer numero,
        @NotBlank String codigoProduto,
        @NotBlank String descricao,
        @NotBlank String ncm,
        @NotBlank String cfop,
        @NotBlank String unidadeComercial,
        @NotNull @Positive BigDecimal quantidade,
        @NotNull @Positive BigDecimal valorUnitario,
        @NotNull @Positive BigDecimal valorTotal,
        @NotNull @Valid ImpostoItemRequest imposto
) {
}
