package com.fiscaladapter.documento.cte;

import com.fiscaladapter.documento.nfe.Endereco;

/** Participante do CT-e (remetente, expedidor, recebedor ou destinatario) - mesma estrutura de dados nos quatro grupos do XSD. */
public record ParticipanteCte(
        String cpfOuCnpj,
        String inscricaoEstadual,
        String razaoSocial,
        Endereco endereco,
        String email
) {
    public String documentoSemMascara() {
        return cpfOuCnpj.replaceAll("\\D", "");
    }

    public boolean ehPessoaJuridica() {
        return documentoSemMascara().length() == 14;
    }
}
