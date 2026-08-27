package com.fiscaladapter.documento.nfse;

/** tcDadosTomador. Endereco/contato sao opcionais no XSD. */
public record TomadorServicoNfse(
        String cpfOuCnpj,
        String inscricaoMunicipal,
        String razaoSocial,
        EnderecoNfse endereco,
        String telefone,
        String email
) {
    public String documentoSemMascara() {
        return cpfOuCnpj.replaceAll("\\D", "");
    }

    public boolean ehPessoaJuridica() {
        return documentoSemMascara().length() == 14;
    }
}
