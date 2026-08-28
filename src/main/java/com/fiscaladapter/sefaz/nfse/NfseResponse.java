package com.fiscaladapter.sefaz.nfse;

/**
 * Resposta de GerarNfseEnvio/ConsultarNfseRpsEnvio (mesmo formato de sucesso
 * - CompNfse/Nfse/InfNfse - e de erro - ListaMensagemRetorno - no XSD ABRASF).
 */
public record NfseResponse(String numeroNfse, String codigoVerificacao, String codigoErro, String mensagemErro) {

    public boolean autorizada() {
        return numeroNfse != null;
    }

    static NfseResponse sucesso(String numeroNfse, String codigoVerificacao) {
        return new NfseResponse(numeroNfse, codigoVerificacao, null, null);
    }

    static NfseResponse erro(String codigoErro, String mensagemErro) {
        return new NfseResponse(null, null, codigoErro, mensagemErro);
    }
}
