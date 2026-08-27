package com.fiscaladapter.documento.mdfe;

public record Condutor(String nome, String cpf) {
    public String cpfSemMascara() {
        return cpf.replaceAll("\\D", "");
    }
}
