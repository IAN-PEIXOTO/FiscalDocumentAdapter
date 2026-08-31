package com.fiscaladapter.distribuicao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * NFe destinada ao CNPJ consultado, ainda nao emitida por ele (FIS-40).
 * dataLimiteManifestacao/diasRestantesParaManifestar/prazoExpirado/
 * alertaProximoDoPrazo sao calculados pelo adapter (nao vem da SEFAZ) a
 * partir da data de autorizacao + PRAZO_MANIFESTACAO_DIAS (ver
 * DistribuicaoDfeService) - ficam nulos quando a data de autorizacao nao
 * veio no resumo (resNFe sem dhRecbto, incomum mas nao impossivel).
 */
public record NfeDestinadaResponse(
        String chaveAcesso,
        String cnpjEmitente,
        String nomeEmitente,
        OffsetDateTime dataEmissao,
        OffsetDateTime dataAutorizacao,
        BigDecimal valorNota,
        String situacao,
        LocalDate dataLimiteManifestacao,
        Long diasRestantesParaManifestar,
        boolean prazoExpirado,
        boolean alertaProximoDoPrazo
) {
}
