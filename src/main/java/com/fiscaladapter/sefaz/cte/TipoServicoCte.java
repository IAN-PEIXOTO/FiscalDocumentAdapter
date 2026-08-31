package com.fiscaladapter.sefaz.cte;

/**
 * So os tres servicos usados pelo modo sincrono do CT-e 4.00 (FIS-44). O modo
 * em lote (CTeRecepcao/CTeRetRecepcao) foi desativado pela SEFAZ em
 * 30/06/2024 (NT 2024.001) - nao ha "consulta de recibo" para mapear aqui.
 */
public enum TipoServicoCte {
    AUTORIZACAO,
    CONSULTA_PROTOCOLO,
    RECEPCAO_EVENTO
}
