package com.fiscaladapter.documento.cte;

import com.fiscaladapter.documento.nfe.TipoAmbiente;

import java.time.LocalDate;

public record IdentificacaoCte(
        String uf,
        String cfop,
        String naturezaOperacao,
        int serie,
        long numero,
        LocalDate dataEmissao,
        TipoAmbiente ambiente,
        String codigoMunicipioInicio,
        String municipioInicio,
        String ufInicio,
        String codigoMunicipioFim,
        String municipioFim,
        String ufFim
) {
}
