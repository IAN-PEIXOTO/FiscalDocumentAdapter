package com.fiscaladapter.documento.nfse;

public record EnderecoNfse(
        String logradouro,
        String numero,
        String complemento,
        String bairro,
        String codigoMunicipio,
        String uf,
        String cep
) {
}
