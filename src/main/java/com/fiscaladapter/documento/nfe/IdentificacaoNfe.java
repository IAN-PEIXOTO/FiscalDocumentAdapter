package com.fiscaladapter.documento.nfe;

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
        String codigoMunicipioFatoGerador
) {
}
