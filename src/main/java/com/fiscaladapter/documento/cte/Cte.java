package com.fiscaladapter.documento.cte;

import com.fiscaladapter.documento.nfe.Emitente;

import java.math.BigDecimal;
import java.util.List;

/**
 * Conhecimento de Transporte Eletronico (modelo 57), modal rodoviario (FIS-18).
 * Outros modais (aereo, aquaviario, ferroviario, dutoviario, multimodal - ver
 * cteModalAereo/Aquaviario/Ferroviario/Dutoviario/MultiModal_v4.00.xsd) ficam
 * para uma evolucao futura desta mesma capacidade.
 */
public record Cte(
        IdentificacaoCte identificacao,
        Emitente emitente,
        ParticipanteCte remetente,
        ParticipanteCte destinatario,
        TipoTomadorServico tomador,
        BigDecimal valorTotalPrestacao,
        BigDecimal valorAReceber,
        ImpostoCte imposto,
        InformacaoCarga informacaoCarga,
        List<NotaFiscalTransportada> notasFiscaisTransportadas,
        String rntrc
) {
}
