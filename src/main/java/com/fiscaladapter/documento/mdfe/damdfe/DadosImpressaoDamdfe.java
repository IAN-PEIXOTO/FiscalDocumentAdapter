package com.fiscaladapter.documento.mdfe.damdfe;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Dados de impressao que nao fazem parte do modelo de dominio do MDF-e em si
 * (mesma logica do DadosImpressaoDanfe da NFe, FIS-8). `encerrado` cobre o
 * criterio de aceite 3 do FIS-49: gerado `false` na emissao (o manifesto
 * acabou de ser autorizado, viagem ainda nao terminou) e `true` quando o
 * DAMDFE e reimpresso apos o encerramento (ver MdfeConsultaController).
 */
public record DadosImpressaoDamdfe(
        String protocoloAutorizacao,
        OffsetDateTime dataHoraAutorizacao,
        boolean encerrado,
        String municipioEncerramento,
        LocalDate dataEncerramento
) {
    public static DadosImpressaoDamdfe deEmissao(String protocoloAutorizacao, OffsetDateTime dataHoraAutorizacao) {
        return new DadosImpressaoDamdfe(protocoloAutorizacao, dataHoraAutorizacao, false, null, null);
    }

    public static DadosImpressaoDamdfe deEncerramento(String protocoloAutorizacao, OffsetDateTime dataHoraAutorizacao,
                                                       String municipioEncerramento, LocalDate dataEncerramento) {
        return new DadosImpressaoDamdfe(protocoloAutorizacao, dataHoraAutorizacao, true, municipioEncerramento, dataEncerramento);
    }

    public boolean autorizado() {
        return protocoloAutorizacao != null;
    }
}
