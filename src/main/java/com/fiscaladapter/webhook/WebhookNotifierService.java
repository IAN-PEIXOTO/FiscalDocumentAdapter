package com.fiscaladapter.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Notifica o consumidor da API via webhook quando uma emissao enfileirada
 * (FIS-25) e concluida - autorizada, rejeitada ou falhou. Best-effort: poucas
 * tentativas com backoff curto, sem fila de redelivery persistente - o
 * consumidor sempre pode consultar o status via GET
 * /api/v1/nfe/assincrono/{id} (EmissaoAssincronaController) se a notificacao
 * se perder de vez, entao o webhook e uma conveniencia, nao a unica fonte de
 * verdade do resultado.
 *
 * Assinatura HMAC-SHA256 (FIS-31): o corpo e assinado com o webhook secret
 * do cliente (gerado no cadastro do webhook, ver ClienteApiService) e enviado
 * no header X-Fiscaladapter-Signature no formato "sha256=<hex>", mesmo
 * padrao adotado por GitHub/Stripe - o consumidor recalcula o HMAC do corpo
 * recebido com o proprio secret e compara, para confirmar que a notificacao
 * realmente veio deste adapter (e nao de terceiro fingindo ser).
 */
@Service
public class WebhookNotifierService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotifierService.class);
    private static final int MAX_TENTATIVAS = 3;
    private static final String ALGORITMO_HMAC = "HmacSHA256";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper;
    private final WebhookUrlValidator webhookUrlValidator;

    public WebhookNotifierService(ObjectMapper objectMapper, WebhookUrlValidator webhookUrlValidator) {
        this.objectMapper = objectMapper;
        this.webhookUrlValidator = webhookUrlValidator;
    }

    /**
     * @param webhookSecret usado para assinar o corpo (HMAC-SHA256); pode ser null se o cliente
     *                       cadastrou a URL antes deste recurso existir - nesse caso o header de
     *                       assinatura simplesmente nao e enviado (o consumidor deve recadastrar
     *                       o webhook para obter um secret e poder validar a autenticidade).
     * @return true se o webhook foi entregue (HTTP 2xx) dentro das tentativas permitidas.
     */
    public boolean notificar(String webhookUrl, String webhookSecret, WebhookEventoPayload evento) {
        String corpo;
        try {
            corpo = objectMapper.writeValueAsString(evento);
        } catch (Exception e) {
            log.error("Falha ao serializar payload do webhook para {}", webhookUrl, e);
            return false;
        }

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                // FIS-90: a URL era validada (bloqueio de SSRF, ver WebhookUrlValidator) so no
                // cadastro do webhook; um dominio que resolvia para um IP publico naquele momento
                // pode ter seu DNS trocado depois para apontar a um endereco interno (DNS
                // rebinding), contornando a protecao do FIS-68. Revalidar aqui, imediatamente
                // antes de cada tentativa de envio, fecha essa janela.
                webhookUrlValidator.validar(webhookUrl);

                HttpRequest.Builder requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));

                if (webhookSecret != null) {
                    requisicao.header("X-Fiscaladapter-Signature", "sha256=" + assinarHmac(corpo, webhookSecret));
                }

                HttpResponse<Void> resposta = httpClient.send(requisicao.build(), HttpResponse.BodyHandlers.discarding());
                if (resposta.statusCode() >= 200 && resposta.statusCode() < 300) {
                    return true;
                }
                log.warn("Webhook {} respondeu HTTP {} na tentativa {}/{}", webhookUrl, resposta.statusCode(), tentativa, MAX_TENTATIVAS);
            } catch (IllegalArgumentException e) {
                // URL passou a apontar para um endereco interno/privado desde o cadastro (ou
                // resolucao DNS mudou entre tentativas) - nao adianta tentar de novo, a URL em si
                // e o problema, nao uma falha transitoria de rede.
                log.error("Webhook {} bloqueado por apontar para endereco interno/privado, entrega cancelada: {}",
                        webhookUrl, e.getMessage());
                return false;
            } catch (Exception e) {
                log.warn("Falha ao entregar webhook em {} na tentativa {}/{}: {}", webhookUrl, tentativa, MAX_TENTATIVAS, e.getMessage());
            }
            if (!aguardarAntesDaProximaTentativa(tentativa)) {
                return false; // FIS-87: thread interrompida durante o backoff - nao tenta de novo
            }
        }
        log.error("Webhook {} nao pode ser entregue apos {} tentativas", webhookUrl, MAX_TENTATIVAS);
        return false;
    }

    private String assinarHmac(String corpo, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITMO_HMAC));
            byte[] assinatura = mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(assinatura);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar payload do webhook", e);
        }
    }

    /** @return true se o backoff terminou normalmente; false se a thread foi interrompida (o chamador deve parar de tentar). */
    private boolean aguardarAntesDaProximaTentativa(int tentativa) {
        try {
            Thread.sleep(Duration.ofSeconds((long) Math.pow(2, tentativa)).toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
