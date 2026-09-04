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

    /**
     * A identidade autenticada (quando disponivel) tem prioridade sobre o parametro de request
     * "client_id" (FIS-69): no filtro da API de recursos, o token Bearer ja foi validado antes
     * deste filtro rodar, entao usar "client_id" ali permitiria a um cliente autenticado escapar
     * do proprio limite so variando esse parametro a cada chamada. O fallback para o parametro
     * so se aplica ao endpoint /oauth2/token (RateLimitFilter tambem roda no chain do
     * Authorization Server), onde o client_id e o proprio identificador do requerente e ainda nao
     * ha uma Authentication populada nesse ponto do fluxo OAuth2 client_credentials.
     */
    private String identificarCliente(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getName();
        }
        return request.getParameter("client_id");
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
