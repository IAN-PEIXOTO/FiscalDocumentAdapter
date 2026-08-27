package com.fiscaladapter.documento.nfse;

import java.time.LocalDate;

/**
 * Recibo Provisorio de Servicos (RPS) - o documento que o prestador gera e
 * declara para a prefeitura para obter a NFS-e (FIS-20). tomador e opcional
 * no XSD (ex.: cupom para consumidor nao identificado).
 */
public record Nfse(
        InfRps rps,
        LocalDate competencia,
        DadosServicoNfse servico,
        PrestadorServicoNfse prestador,
        TomadorServicoNfse tomador,
        boolean optanteSimplesNacional,
        boolean incentivoFiscal
) {
}
