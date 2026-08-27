package com.fiscaladapter.sefaz.nfe;

public record StatusServicoResponse(String codigoStatus, String motivo, boolean servicoEmOperacao) {

    public static StatusServicoResponse de(String codigoStatus, String motivo) {
        // cStat 107 = "Servico em Operacao" - unico codigo que indica disponibilidade normal
        return new StatusServicoResponse(codigoStatus, motivo, "107".equals(codigoStatus));
    }
}
