package com.fiscaladapter.documento.nfse;

/** IdentificacaoPrestador do RPS: apenas CPF/CNPJ + inscricao municipal (endereco nao entra no RPS, so na NFS-e de resposta). */
public record PrestadorServicoNfse(String cpfOuCnpj, String inscricaoMunicipal) {
    public String documentoSemMascara() {
        return cpfOuCnpj.replaceAll("\\D", "");
    }

    public boolean ehPessoaJuridica() {
        return documentoSemMascara().length() == 14;
    }
}
