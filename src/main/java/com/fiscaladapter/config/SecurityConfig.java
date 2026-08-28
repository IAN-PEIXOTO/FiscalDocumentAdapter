package com.fiscaladapter.config;

import com.fiscaladapter.observabilidade.MdcRequisicaoFilter;
import com.fiscaladapter.seguranca.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;

import java.time.Duration;

/**
 * Protege os endpoints da API com Bearer token (JWT) emitido pelo nosso
 * proprio Authorization Server (ver AuthorizationServerConfig, FIS-15).
 * /health fica publico para health check de infraestrutura.
 *
 * Cabecalhos de resposta (FIS-14): HSTS forca HTTPS em clientes que ja
 * visitaram a API, no-store impede que respostas com dados fiscais sensiveis
 * (chave de acesso, XML assinado) fiquem em cache de proxy/navegador, e
 * X-Content-Type-Options/frame-options sao hardening padrao contra
 * sniffing/clickjacking (esta API nao serve HTML, mas nao custa).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter,
                                                        MdcRequisicaoFilter mdcRequisicaoFilter) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health", "/actuator/health").permitAll()
                        .requestMatchers("/api/v1/nfe", "/api/v1/nfe/**").hasAuthority("SCOPE_nfe")
                        .requestMatchers("/api/v1/certificados", "/api/v1/certificados/**").hasAuthority("SCOPE_nfe")
                        .requestMatchers("/api/v1/webhook", "/api/v1/webhook/**").hasAuthority("SCOPE_nfe")
                        .requestMatchers("/api/v1/documentos/**").hasAuthority("SCOPE_nfe")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds()))
                        .contentTypeOptions(contentTypeOptions -> {})
                        .frameOptions(frameOptions -> frameOptions.deny())
                        .cacheControl(cacheControl -> {})
                        .addHeaderWriter(new StaticHeadersWriter("X-Permitted-Cross-Domain-Policies", "none")))
                .addFilterBefore(rateLimitFilter, AuthorizationFilter.class)
                .addFilterBefore(mdcRequisicaoFilter, AuthorizationFilter.class);
        return http.build();
    }
}
