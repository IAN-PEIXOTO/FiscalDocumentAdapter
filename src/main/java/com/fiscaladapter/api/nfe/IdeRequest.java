package com.fiscaladapter.api.nfe;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Espelha NfeSefazIde do schema da API ACBr. cNF e cDV nao aparecem aqui
 * porque sao calculados por nos (chave de acesso), assim como no ACBr.
 */
public record IdeRequest(
        @NotNull Integer cUF,
        @NotBlank String natOp,
        @NotNull @Positive Integer serie,
        @NotNull @Positive Long nNF,
        @NotNull LocalDate dhEmi,
        @NotNull Integer tpNF,
        @NotNull Integer idDest,
        @NotBlank String cMunFG,
        @NotNull Integer tpImp,
        @NotNull Integer tpEmis,
        @NotNull Integer tpAmb,
        @NotNull Integer finNFe,
        @NotNull Integer indFinal,
        @NotNull Integer indPres,
        @NotNull Integer procEmi,
        @NotBlank String verProc,
        /**
         * FIS-115: 0=sem intermediador, 1=via site/plataforma de terceiros (marketplace) - nulo
         * (payload antigo, campo omitido) e tratado pelo mapper como "0", o caso mais comum.
         */
        Integer indIntermed
) {
    /** Sem indIntermed explicito - mantido para nao quebrar chamadores existentes (FIS-115). */
    public IdeRequest(Integer cUF, String natOp, Integer serie, Long nNF, LocalDate dhEmi, Integer tpNF,
                       Integer idDest, String cMunFG, Integer tpImp, Integer tpEmis, Integer tpAmb, Integer finNFe,
                       Integer indFinal, Integer indPres, Integer procEmi, String verProc) {
        this(cUF, natOp, serie, nNF, dhEmi, tpNF, idDest, cMunFG, tpImp, tpEmis, tpAmb, finNFe, indFinal, indPres,
                procEmi, verProc, null);
    }
}
