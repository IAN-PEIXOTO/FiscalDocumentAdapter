package com.fiscaladapter.documento.nfe.danfe;

import java.time.OffsetDateTime;

/**
 * Dados de impressao que nao fazem parte do modelo de dominio da NFe em si
 * (protocolo de autorizacao so existe depois que o FIS-42 - webservice da
 * SEFAZ - transmitir o documento; ate la, protocolo/dataAutorizacao ficam
 * nulos e o DANFE e impresso sem eles, o que e uma representacao honesta do
 * estado atual do pipeline).
 */
public record DadosImpressaoDanfe(
        OrientacaoDanfe orientacao,
        boolean contingencia,
        String protocoloAutorizacao,
        OffsetDateTime dataHoraAutorizacao
) {
    public static DadosImpressaoDanfe semAutorizacao(OrientacaoDanfe orientacao, boolean contingencia) {
        return new DadosImpressaoDanfe(orientacao, contingencia, null, null);
    }

    public boolean autorizada() {
        return protocoloAutorizacao != null;
    }
}
