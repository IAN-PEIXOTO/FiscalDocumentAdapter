package com.fiscaladapter.sefaz.nfe;

public record EpecResponse(String codigoStatus, String motivo, boolean registrada) {

    public static EpecResponse de(String codigoStatus, String motivo) {
        // cStat 136 = "Evento registrado e vinculado a NF-e" especifico do EPEC
        // (cancelamento/CC-e usam 135 para o mesmo significado - codigo distinto documentado
        // de forma consistente nas implementacoes de referencia, ex.: nfephp-org/sped-nfe).
        return new EpecResponse(codigoStatus, motivo, "136".equals(codigoStatus));
    }
}
