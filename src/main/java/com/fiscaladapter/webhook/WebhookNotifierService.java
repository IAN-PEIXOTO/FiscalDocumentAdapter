package com.fiscaladapter.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Notifica o consumidor da API via webhook quando uma emissao enfileirada
 * (FIS-25) e concluida - autorizada, rejeitada ou falhou. Best-effort: poucas
 * tentativas com backoff curto, sem fila de redelivery persistente - o
 * consumidor sempre pode consultar o status via GET
 * /api/v1/nfe/assincrono/{id} (EmissaoAssincronaController) se a notificacao
 * se perder de vez, entao o webhook e uma conveniencia, nao a unica fonte de
 * verdade do resultado.
 */
@Service
public class WebhookNotifierService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifierService.class);
    private static final int MAX_TENTATIVAS = 3;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;

    public WebhookNotifierService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return true se o webhook foi entregue (HTTP 2xx) dentro das tentativas permitidas. */
    public boolean notificar(String webhookUrl, WebhookEventoPayload evento) {
        String corpo;
        try {
            corpo = objectMapper.writeValueAsString(evento);
        } catch (Exception e) {
            log.error("Falha ao serializar payload do webhook para {}", webhookUrl, e);
            return false;
        }

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                HttpRequest requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<Void> resposta = httpClient.send(requisicao, HttpResponse.BodyHandlers.discarding());
                if (resposta.statusCode() >= 200 && resposta.statusCode() < 300) {
                    return true;
                }
                log.warn("Webhook {} respondeu HTTP {} na tentativa {}/{}", webhookUrl, resposta.statusCode(), tentativa, MAX_TENTATIVAS);
            } catch (Exception e) {
                log.warn("Falha ao entregar webhook em {} na tentativa {}/{}: {}", webhookUrl, tentativa, MAX_TENTATIVAS, e.getMessage());
            }
            aguardarAntesDaProximaTentativa(tentativa);
        }
        log.error("Webhook {} nao pode ser entregue apos {} tentativas", webhookUrl, MAX_TENTATIVAS);
        return false;
    }

    private void aguardarAntesDaProximaTentativa(int tentativa) {
        try {
            Thread.sleep(Duration.ofSeconds((long) Math.pow(2, tentativa)).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
