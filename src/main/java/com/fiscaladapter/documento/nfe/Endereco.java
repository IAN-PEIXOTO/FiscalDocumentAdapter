package com.fiscaladapter.documento.nfe;

public record Endereco(
        String logradouro,
        String numero,
        String bairro,
        String codigoMunicipio,
        String municipio,
        String uf,
        String cep,
        String telefone
) {
}
