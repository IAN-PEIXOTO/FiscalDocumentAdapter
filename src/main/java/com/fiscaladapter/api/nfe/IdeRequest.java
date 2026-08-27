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
        @NotBlank String verProc
) {
}
