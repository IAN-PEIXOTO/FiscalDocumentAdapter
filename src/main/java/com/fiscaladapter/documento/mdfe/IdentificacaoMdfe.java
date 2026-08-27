package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.TipoAmbiente;

import java.time.LocalDate;

public record IdentificacaoMdfe(
        String uf,
        int serie,
        long numero,
        LocalDate dataEmissao,
        TipoAmbiente ambiente,
        String ufInicio,
        String ufFim,
        String codigoMunicipioCarregamento,
        String municipioCarregamento
) {
}
