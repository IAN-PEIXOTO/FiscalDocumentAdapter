package com.fiscaladapter.api.nfe.assincrono;

import com.fiscaladapter.api.nfe.NfeResponse;

/** resultado vem nulo enquanto PENDENTE/PROCESSANDO ou quando FALHA (nesse caso ver erroMensagem). */
public record EmissaoAssincronaStatusResponse(Long id, StatusEmissaoAssincrona status, NfeResponse resultado, String erroMensagem) {
}
