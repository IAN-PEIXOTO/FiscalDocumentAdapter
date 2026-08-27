package com.fiscaladapter.observabilidade;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Coloca o client_id autenticado no MDC para correlacionar todas as linhas de
 * log de uma mesma requisicao (FIS-11). Roda depois da autenticacao (ver
 * SecurityConfig - registrado antes do AuthorizationFilter, que so age depois
 * que o filtro de autenticacao do resource server ja populou o
 * SecurityContext). A chave de acesso da NFe e adicionada separadamente
 * dentro de MdcChaveAcesso, ja que so existe depois que o documento e gerado.
 */
@Component
public class MdcRequisicaoFilter extends OncePerRequestFilter {

    static final String CHAVE_MDC_CLIENT_ID = "clientId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                MDC.put(CHAVE_MDC_CLIENT_ID, authentication.getName());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CHAVE_MDC_CLIENT_ID);
        }
    }
}
