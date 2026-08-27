package com.fiscaladapter.api.nfe;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Espelha NfeSefazInfNFe. O campo "total" nao aparece aqui de proposito: no
 * ACBr ele e obrigatorio no payload (o cliente calcula e envia), mas aqui
 * preferimos sempre calcular os totais no servidor a partir dos itens, para
 * nao confiar em soma de imposto vinda do cliente. Essa e a unica divergencia
 * deliberada do contrato do ACBr.
 */
public record InfNfeRequest(
        @NotNull @Valid IdeRequest ide,
        @NotNull @Valid EmitRequest emit,
        @NotNull @Valid DestRequest dest,
        @NotEmpty List<@Valid DetRequest> det,
        @NotNull @Valid TranspRequest transp,
        @NotNull @Valid PagRequest pag
) {
}
