package com.fiscaladapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ATENCAO: autenticacao real (client_id/client_secret) ainda nao foi
 * implementada (ver FIS-15). Ate la, a API fica aberta para permitir o
 * desenvolvimento/teste do fluxo de emissao. NAO subir para producao
 * enquanto o FIS-15 nao estiver concluido e este permitAll removido.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
