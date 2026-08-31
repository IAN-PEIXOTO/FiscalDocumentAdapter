package com.fiscaladapter.sefaz.mdfe;

/**
 * So os tres servicos usados pelo modo sincrono do MDF-e 3.00 (FIS-45). O
 * modo em lote (MDFeRecepcao/MDFeRetRecepcao) foi desativado pela SEFAZ em
 * 30/06/2024 (NT 2024.001), mesma migracao do CT-e (FIS-44).
 */
public enum TipoServicoMdfe {
    AUTORIZACAO,
    CONSULTA_PROTOCOLO,
    RECEPCAO_EVENTO
}
