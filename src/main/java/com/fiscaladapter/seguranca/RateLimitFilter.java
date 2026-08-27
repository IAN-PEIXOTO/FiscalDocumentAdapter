package com.fiscaladapter.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
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

    private String identificarCliente(HttpServletRequest request) {
        String clientIdParam = request.getParameter("client_id");
        if (clientIdParam != null) {
            return clientIdParam;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
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

    private static final class Janela {
        final long minuto;
        final AtomicInteger contador = new AtomicInteger(0);

        Janela(long minuto) {
            this.minuto = minuto;
        }
    }
}
