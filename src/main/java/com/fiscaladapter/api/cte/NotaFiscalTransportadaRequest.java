package com.fiscaladapter.api.cte;

import jakarta.validation.constraints.NotBlank;

public record NotaFiscalTransportadaRequest(@NotBlank String chave) {
}
