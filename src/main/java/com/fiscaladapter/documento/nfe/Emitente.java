package com.fiscaladapter.documento.nfe;

public record Emitente(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String inscricaoEstadual,
        String regimeTributario,
        Endereco endereco
) {
    public String cnpjSemMascara() {
        return cnpj.replaceAll("\\D", "");
    }
}
