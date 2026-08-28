package com.fiscaladapter.sefaz.nfse;

/** Resposta de CancelarNfseEnvio (RetCancelamento em caso de sucesso, ListaMensagemRetorno em caso de erro). */
public record CancelamentoNfseResponse(String dataHoraCancelamento, String codigoErro, String mensagemErro) {

    public boolean cancelada() {
        return dataHoraCancelamento != null;
    }

    static CancelamentoNfseResponse sucesso(String dataHoraCancelamento) {
        return new CancelamentoNfseResponse(dataHoraCancelamento, null, null);
    }

    static CancelamentoNfseResponse erro(String codigoErro, String mensagemErro) {
        return new CancelamentoNfseResponse(null, codigoErro, mensagemErro);
    }
}
