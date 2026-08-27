package com.fiscaladapter.sefaz.nfe;

public record ManifestacaoResponse(String codigoStatus, String motivo, String numeroProtocolo, boolean registrada) {

    public static ManifestacaoResponse de(String codigoStatus, String motivo, String numeroProtocolo) {
        // cStat 135 = "Evento registrado e vinculado a NF-e" (mesmo codigo de cancelamento/CC-e - a
        // manifestacao nao e EPEC, entao nao usa o cStat 136 especifico daquele evento)
        return new ManifestacaoResponse(codigoStatus, motivo, numeroProtocolo, "135".equals(codigoStatus));
    }
}
