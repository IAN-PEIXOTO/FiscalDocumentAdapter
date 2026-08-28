package com.fiscaladapter.webhook;

/**
 * Corpo do POST enviado ao webhook do cliente (FIS-25). tipo: "nfe.autorizada",
 * "nfe.rejeitada" ou "nfe.falha" - "nfe.cancelada" fica reservado para quando
 * existir um endpoint de cancelamento de NFe exposto na API (hoje nao ha).
 */
public record WebhookEventoPayload(
        String tipo,
        Long idEmissaoAssincrona,
        String chaveAcesso,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        String mensagemErro
) {
}
