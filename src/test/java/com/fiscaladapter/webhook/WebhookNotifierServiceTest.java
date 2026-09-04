package com.fiscaladapter.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIS-90: WebhookUrlValidator (protecao contra SSRF do FIS-68) so era chamado no cadastro do
 * webhook, nunca no envio - uma URL que resolvia para IP publico no cadastro e depois passa a
 * resolver para um endereco interno (DNS rebinding) contornava a protecao por completo. Prova
 * que a entrega revalida a URL a cada tentativa e nao chega a fazer nenhuma chamada de rede
 * quando o destino aponta para um endereco interno/privado.
 */
class WebhookNotifierServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebhookUrlValidator webhookUrlValidator = new WebhookUrlValidator();
    private final WebhookNotifierService service = new WebhookNotifierService(objectMapper, webhookUrlValidator);

    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void naoDeveEntregarWebhookQuandoUrlApontaParaEnderecoInterno() {
        boolean entregue = service.notificar("http://127.0.0.1:1/webhook", null, evento());

        assertThat(entregue).isFalse();
    }

    @Test
    void naoDeveTentarNovamenteQuandoUrlEstaBloqueada() throws Exception {
        // se a validacao falhar de forma persistente (URL sempre invalida), nao ha por que gastar
        // as 3 tentativas com backoff exponencial - a URL em si e o problema, nao a rede.
        AtomicInteger chamadasRecebidas = new AtomicInteger(0);
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/webhook", exchange -> {
            chamadasRecebidas.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        servidor.start();

        // localhost e bloqueado pelo WebhookUrlValidator (endereco loopback) mesmo apontando para
        // um servidor real e funcional - prova que a checagem acontece antes de qualquer tentativa
        // de conexao, nao so quando a rede falha.
        boolean entregue = service.notificar(
                "http://localhost:" + servidor.getAddress().getPort() + "/webhook", null, evento());

        assertThat(entregue).isFalse();
        assertThat(chamadasRecebidas.get()).isZero();
    }

    private WebhookEventoPayload evento() {
        return new WebhookEventoPayload("evt-1", "nfe.autorizada", 1L, "chave", true, "100", "Autorizado", "protocolo", null);
    }
}
