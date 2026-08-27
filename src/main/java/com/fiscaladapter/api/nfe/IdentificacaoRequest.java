package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record IdentificacaoRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String uf,
        @NotBlank String naturezaOperacao,
        @NotNull @Positive Integer serie,
        @NotNull @Positive Long numero,
        @NotNull LocalDate dataEmissao,
        @NotBlank @Pattern(regexp = "PRODUCAO|HOMOLOGACAO") String ambiente,
        @NotNull @Positive Integer finalidadeEmissao,
        @NotNull Boolean consumidorFinal,
        @NotBlank @Pattern(regexp = "\\d{7}") String codigoMunicipioFatoGerador
) {
}
