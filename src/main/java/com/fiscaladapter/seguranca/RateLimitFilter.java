package com.fiscaladapter.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting simples por client_id (janela fixa de 1 minuto). Implementacao
 * em memoria: funciona para uma unica instancia da aplicacao. Em producao com
 * multiplas instancias, isso precisa migrar para um contador compartilhado
 * (ex.: Redis) - registrar como debito tecnico junto com o epico de
 * observabilidade/infra.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMITE_POR_MINUTO = 60;

    private final ConcurrentHashMap<String, Janela> janelasPorCliente = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String clientId = identificarCliente(request);
        if (clientId != null && excedeuLimite(clientId)) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"mensagem\":\"Limite de requisicoes excedido para este client_id\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * A identidade autenticada (quando disponivel) tem prioridade sobre o parametro de request
     * "client_id" (FIS-69): no filtro da API de recursos, o token Bearer ja foi validado antes
     * deste filtro rodar, entao usar "client_id" ali permitiria a um cliente autenticado escapar
     * do proprio limite so variando esse parametro a cada chamada. O fallback so se aplica ao
     * endpoint /oauth2/token (RateLimitFilter tambem roda no chain do Authorization Server), onde
     * o client_id e o proprio identificador do requerente e ainda nao ha uma Authentication
     * populada nesse ponto do fluxo OAuth2 client_credentials.
     *
     * FIS-89: nesse fallback, o client_id tambem precisa ser lido do header "Authorization: Basic"
     * (metodo CLIENT_SECRET_BASIC, o unico registrado em ClienteApiRegisteredClientRepository e o
     * unico usado pelos clientes de fato) - so olhar o parametro de request deixava toda tentativa
     * via Basic auth com clientId nulo, escapando do rate limit e permitindo brute-force irrestrito
     * do client_secret.
     */
    private String identificarCliente(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        String clientIdDoBasicAuth = extrairClientIdDoBasicAuth(request);
        if (clientIdDoBasicAuth != null) {
            return clientIdDoBasicAuth;
        }
        return request.getParameter("client_id");
    }

    private String extrairClientIdDoBasicAuth(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        try {
            String credenciais = new String(Base64.getDecoder().decode(header.substring(6).trim()), StandardCharsets.UTF_8);
            int separador = credenciais.indexOf(':');
            String clientId = separador < 0 ? credenciais : credenciais.substring(0, separador);
            return URLDecoder.decode(clientId, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null; // header Basic malformado - deixa o fallback de parametro/limite generico decidir
        }
    }

    private boolean excedeuLimite(String clientId) {
        long minutoAtual = Instant.now().getEpochSecond() / 60;
        Janela janela = janelasPorCliente.compute(clientId, (id, atual) -> {
            if (atual == null || atual.minuto != minutoAtual) {
                return new Janela(minutoAtual);
            }
            return atual;
        });
        return janela.contador.incrementAndGet() > LIMITE_POR_MINUTO;
    }

    /**
     * FIS-99: sem isso, janelasPorCliente cresce sem limite - toda chave distinta ja vista (nesse
     * fallback nao autenticado, um client_id arbitrario escolhido pelo proprio chamador) ganha uma
     * entrada que so e substituida, nunca removida. Roda a cada 5 minutos e descarta qualquer
     * janela de um minuto anterior ao atual (uma janela so importa durante o proprio minuto).
     */
    @Scheduled(fixedRate = 300_000)
    public void expurgarJanelasExpiradas() {
        long minutoAtual = Instant.now().getEpochSecond() / 60;
        janelasPorCliente.entrySet().removeIf(entry -> entry.getValue().minuto < minutoAtual);
    }

    private static final class Janela {
        final long minuto;
        final AtomicInteger contador = new AtomicInteger(0);

        Janela(long minuto) {
            this.minuto = minuto;
        }
    }
}
