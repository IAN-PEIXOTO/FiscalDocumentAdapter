package com.fiscaladapter.api.nfse;

/** Ver javadoc de {@link CancelamentoNfseApiResponse} sobre codigoErro/mensagemErro. */
public record ConsultaNfseApiResponse(
        boolean autorizada,
        String numeroNfse,
        String codigoVerificacao,
        String codigoErro,
        String mensagemErro
) {
}
