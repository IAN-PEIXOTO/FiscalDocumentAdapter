package com.fiscaladapter.webhook;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Bloqueia URLs de webhook que apontem para destinos internos (SSRF, FIS-68): resolve o host e
 * rejeita se qualquer endereco IP resolvido for loopback, link-local (inclui o endpoint de
 * metadados de nuvem 169.254.169.254), de rede privada (RFC 1918/site-local), coringa ou
 * multicast. Resolve TODOS os enderecos do host (nao so o primeiro) para nao ser contornado por
 * um DNS que responde varios enderecos.
 *
 * O HttpClient usado para notificar (ver WebhookNotifierService/SefazHttpClientFactory - aqui e
 * um HttpClient.newBuilder() simples) ja usa a politica padrao do java.net.http, que e NAO seguir
 * redirecionamentos automaticamente - entao nao ha bypass desta validacao via redirect HTTP.
 */
@Component
public class WebhookUrlValidator {

    public void validar(String url) {
        String host = URI.create(url).getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL de webhook sem host valido: " + url);
        }

        InetAddress[] enderecos;
        try {
            enderecos = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Nao foi possivel resolver o host do webhook: " + host);
        }

        for (InetAddress endereco : enderecos) {
            if (endereco.isLoopbackAddress() || endereco.isLinkLocalAddress() || endereco.isSiteLocalAddress()
                    || endereco.isAnyLocalAddress() || endereco.isMulticastAddress()) {
                throw new IllegalArgumentException(
                        "URL de webhook aponta para um endereco de rede interno/privado, nao permitido: " + host);
            }
        }
    }
}
