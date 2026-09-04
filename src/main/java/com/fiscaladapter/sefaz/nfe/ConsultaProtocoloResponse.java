package com.fiscaladapter.sefaz.nfe;

import java.util.Set;

/** dhRecbto (data/hora de autorizacao) e usado pelo NfeConsultaController para aplicar o prazo de cancelamento especifico da NFC-e (FIS-43). */
public record ConsultaProtocoloResponse(String codigoStatus, String motivo, String numeroProtocolo, String dhRecbto, boolean autorizada) {

    /**
     * FIS-88: mesmo conjunto de codigos de sucesso de {@link AutorizacaoResponse#de}. cStat 150
     * ("autorizado fora de prazo") e um documento legalmente valido; sem reconhecer isso aqui, uma
     * consulta de protocolo (ou a recuperacao de duplicidade em EmissaoNfeOrquestrador) reportava
     * como rejeitado um documento que a SEFAZ ja tinha autorizado.
     */
    private static final Set<String> CSTAT_SUCESSO = Set.of("100", "150");

    public static ConsultaProtocoloResponse de(String codigoStatus, String motivo, String numeroProtocolo, String dhRecbto) {
        return new ConsultaProtocoloResponse(codigoStatus, motivo, numeroProtocolo, dhRecbto, CSTAT_SUCESSO.contains(codigoStatus));
    }
}
