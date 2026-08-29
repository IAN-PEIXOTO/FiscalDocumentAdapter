package com.fiscaladapter.webhook;

/** secret e devolvido so nesta resposta (FIS-31) - guarde para validar o HMAC das notificacoes recebidas. */
public record WebhookCadastradoResponse(String url, String secret) {
}
