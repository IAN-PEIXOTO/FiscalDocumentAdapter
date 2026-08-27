package com.fiscaladapter.documento.nfe;

import java.math.BigDecimal;

/**
 * Subconjunto dos tributos por item exigidos pelo layout da NFe.
 *
 * grupoIcms e o nome literal do grupo XML do ICMS (ex.: "ICMS00", "ICMS40",
 * "ICMSSN102" - ver NfeXmlGenerator.escreverIcms), ja que cada grupo tem uma
 * tag pai e um subconjunto de campos diferente no XSD. codigoIcms carrega o
 * CST (ICMS00/ICMS40) ou CSOSN (ICMSSN*), conforme o grupo. baseCalculoIcms/
 * aliquotaIcms/valorIcms ficam zerados quando o grupo nao os utiliza (isenta,
 * nao tributada, Simples Nacional sem permissao de credito).
 */
public record ImpostoItem(
        String grupoIcms,
        String origemIcms,
        String codigoIcms,
        BigDecimal baseCalculoIcms,
        BigDecimal aliquotaIcms,
        BigDecimal valorIcms,
        BigDecimal valorIpi,
        BigDecimal valorPis,
        BigDecimal valorCofins
) {
    public BigDecimal valorTotalTributos() {
        return valorIcms.add(valorIpi).add(valorPis).add(valorCofins);
    }
}
