package com.fiscaladapter.config;

import com.fiscaladapter.seguranca.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Protege os endpoints da API com Bearer token (JWT) emitido pelo nosso
 * proprio Authorization Server (ver AuthorizationServerConfig, FIS-15).
 * /health fica publico para health check de infraestrutura.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/health").permitAll()
                        .requestMatchers("/api/v1/nfe").hasAuthority("SCOPE_nfe")
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .addFilterBefore(rateLimitFilter, AuthorizationFilter.class);
        return http.build();
    }
}
