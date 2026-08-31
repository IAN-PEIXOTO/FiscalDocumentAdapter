package com.fiscaladapter.documento.nfce.danfe;

import java.time.OffsetDateTime;

/**
 * Dados de impressao que nao fazem parte do modelo de dominio da NFC-e em si
 * (mesma logica do DadosImpressaoDanfe da NFe, FIS-8) - conteudoQrCode e
 * urlConsultaPublica vem de NfceQrCodeService/NfceQrCodeUrlRegistry (FIS-17),
 * ja calculados na emissao (ver NfceEmissaoService, FIS-43).
 */
public record DadosImpressaoDanfeNfce(
        boolean contingencia,
        String protocoloAutorizacao,
        OffsetDateTime dataHoraAutorizacao,
        String conteudoQrCode,
        String urlConsultaPublica
) {
    public boolean autorizada() {
        return protocoloAutorizacao != null;
    }
}
