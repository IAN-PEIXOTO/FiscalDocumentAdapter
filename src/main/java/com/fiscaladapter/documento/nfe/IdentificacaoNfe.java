package com.fiscaladapter.documento.nfe;

import com.fiscaladapter.documento.TipoDocumentoFiscal;

import java.time.LocalDate;

public record IdentificacaoNfe(
        String uf,
        String naturezaOperacao,
        int serie,
        long numero,
        LocalDate dataEmissao,
        TipoAmbiente ambiente,
        int finalidadeEmissao,
        boolean consumidorFinal,
        String codigoMunicipioFatoGerador,
        TipoDocumentoFiscal tipoDocumento,
        /**
         * FIS-115: indicador de intermediador/marketplace (indIntermed), obrigatorio pela SEFAZ
         * em producao/homologacao real (rejeicao cStat 434 quando ausente) - "0" = operacao em
         * site/plataforma propria, "1" = em site/plataforma de terceiros (exige tambem
         * NotaFiscalEletronica.intermediador() preenchido, ver RVN-007).
         */
        String indicadorIntermediador
) {
    /** Sem indicador de intermediador explicito - assume "0" (sem intermediador), o caso mais comum. */
    public IdentificacaoNfe(String uf, String naturezaOperacao, int serie, long numero, LocalDate dataEmissao,
                             TipoAmbiente ambiente, int finalidadeEmissao, boolean consumidorFinal,
                             String codigoMunicipioFatoGerador, TipoDocumentoFiscal tipoDocumento) {
        this(uf, naturezaOperacao, serie, numero, dataEmissao, ambiente, finalidadeEmissao, consumidorFinal,
                codigoMunicipioFatoGerador, tipoDocumento, "0");
    }
}
