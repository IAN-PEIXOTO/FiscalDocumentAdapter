package com.fiscaladapter.api.cte;

import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** Espelha um dos quatro grupos de participante do CT-e (remetente, destinatario, etc. - mesma estrutura nos quatro). */
public record ParticipanteCteRequest(
        String CNPJ,
        String CPF,
        String IE,
        @NotBlank String xNome,
        @Valid EnderecoNfeRequest endereco,
        String email
) {
}
