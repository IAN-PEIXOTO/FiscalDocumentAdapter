package com.fiscaladapter.documento.cte.dacte;

import java.time.OffsetDateTime;

/**
 * Dados de impressao que nao fazem parte do modelo de dominio do CT-e em si
 * (mesma logica do DadosImpressaoDanfe da NFe, FIS-8) - protocolo/data de
 * autorizacao so existem depois da transmissao (FIS-44).
 */
public record DadosImpressaoDacte(
        String protocoloAutorizacao,
        OffsetDateTime dataHoraAutorizacao
) {
    public static DadosImpressaoDacte semAutorizacao() {
        return new DadosImpressaoDacte(null, null);
    }

    public boolean autorizada() {
        return protocoloAutorizacao != null;
    }
}
