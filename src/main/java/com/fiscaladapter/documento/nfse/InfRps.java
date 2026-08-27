package com.fiscaladapter.documento.nfse;

import java.time.LocalDate;

/** tcInfRps: status 1=Normal, 2=Cancelado. */
public record InfRps(IdentificacaoRps identificacao, LocalDate dataEmissao, int status) {
}
