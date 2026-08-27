package com.fiscaladapter.documento.nfe;

public record Destinatario(
        String cpfOuCnpj,
        String razaoSocial,
        String indicadorInscricaoEstadual,
        String inscricaoEstadual,
        String email,
        Endereco endereco
) {
    public String documentoSemMascara() {
        return cpfOuCnpj.replaceAll("\\D", "");
    }

    public boolean ehPessoaJuridica() {
        return documentoSemMascara().length() == 14;
    }
}
