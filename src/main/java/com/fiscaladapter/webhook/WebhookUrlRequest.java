package com.fiscaladapter.webhook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** https e fortemente recomendado em producao (o payload inclui chave de acesso/status fiscal); http aceito so para testes locais. */
public record WebhookUrlRequest(
        @NotBlank
        @Pattern(regexp = "^https?://.+", message = "url deve comecar com http:// ou https://")
        String url
) {
}
