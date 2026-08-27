package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnderecoRequest(
        @NotBlank String logradouro,
        @NotBlank String numero,
        @NotBlank String bairro,
        @NotBlank @Pattern(regexp = "\\d{7}", message = "codigoMunicipio deve ter 7 digitos (codigo IBGE)") String codigoMunicipio,
        @NotBlank String municipio,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "uf deve ter 2 letras maiusculas") String uf,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "cep deve ter 8 digitos") String cep,
        String telefone
) {
}
