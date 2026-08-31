package com.fiscaladapter.sefaz.nfe;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Resumo (resNFe) de uma NFe destinada ao CNPJ consultado, devolvido pela NFeDistribuicaoDFe (FIS-40). */
public record ResumoNfeDistribuicao(
        String chaveAcesso,
        String cnpjEmitente,
        String nomeEmitente,
        OffsetDateTime dataEmissao,
        OffsetDateTime dataAutorizacao,
        BigDecimal valorNota,
        SituacaoNfeDistribuicao situacao
) {
}
