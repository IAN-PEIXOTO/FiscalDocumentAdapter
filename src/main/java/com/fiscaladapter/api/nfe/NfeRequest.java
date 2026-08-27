package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NfeRequest(
        @NotNull @Valid IdentificacaoRequest identificacao,
        @NotNull @Valid EmitenteRequest emitente,
        @NotNull @Valid DestinatarioRequest destinatario,
        @NotEmpty List<@Valid ItemRequest> itens,
        @NotEmpty List<@Valid PagamentoRequest> pagamentos
) {
}
