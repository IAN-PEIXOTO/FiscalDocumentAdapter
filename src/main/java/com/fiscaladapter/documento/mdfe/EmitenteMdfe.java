package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.Endereco;

/** Emitente do MDF-e (grupo emit do XSD - sem CRT, diferente do emitente de NFe/CT-e). */
public record EmitenteMdfe(
        String cnpj,
        String razaoSocial,
        String nomeFantasia,
        String inscricaoEstadual,
        Endereco endereco
) {
    public String cnpjSemMascara() {
        return cnpj.replaceAll("\\D", "");
    }
}
