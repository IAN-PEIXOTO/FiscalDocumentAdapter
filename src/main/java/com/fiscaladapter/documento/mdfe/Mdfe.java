package com.fiscaladapter.documento.mdfe;

import java.math.BigDecimal;
import java.util.List;

/**
 * Manifesto Eletronico de Documentos Fiscais (modelo 58), modal rodoviario,
 * emissao normal (FIS-19). Suporta um unico municipio de carregamento e um
 * unico municipio de descarregamento (infMunCarrega/infMunDescarga permitem
 * varios no XSD - varios municipios ficam para uma evolucao futura, assim
 * como os outros modais: aereo, aquaviario, ferroviario).
 */
public record Mdfe(
        IdentificacaoMdfe identificacao,
        EmitenteMdfe emitente,
        String rntrc,
        VeiculoTracao veiculoTracao,
        List<Condutor> condutores,
        String codigoMunicipioDescarga,
        String municipioDescarga,
        List<String> chavesCteTransportados,
        List<String> chavesNfeTransportadas,
        BigDecimal valorCarga,
        BigDecimal pesoBrutoKg
) {
}
