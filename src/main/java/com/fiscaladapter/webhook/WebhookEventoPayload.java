package com.fiscaladapter.webhook;

/**
 * Corpo do POST enviado ao webhook do cliente (FIS-25/FIS-31). tipo: "nfe.autorizada",
 * "nfe.rejeitada" ou "nfe.falha" - "nfe.cancelada" fica reservado para quando
 * existir um endpoint de cancelamento de NFe exposto na API (hoje nao ha).
 *
 * eventoId e um UUID unico por notificacao - o consumidor deve guarda-lo e
 * ignorar reentregas com o mesmo id (garantia de idempotencia do lado dele,
 * ja que este adapter pode reenviar o mesmo evento em caso de retry de
 * entrega - ver WebhookNotifierService).
 */
public record WebhookEventoPayload(
        String eventoId,
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
