package com.fiscaladapter.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.fiscaladapter.seguranca.RateLimitFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.UUID;

/**
 * Servidor de autorizacao OAuth2 (client_credentials) para autenticacao de
 * clientes da API (FIS-15), no mesmo padrao que a API ACBr usa.
 */
@Configuration
public class AuthorizationServerConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationServerConfig.class);

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http, RateLimitFilter rateLimitFilter) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.addFilterAfter(rateLimitFilter, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    /**
     * FIS-97: a chave RSA usada para assinar/validar os JWTs emitidos precisa ser a MESMA em todas
     * as instancias da aplicacao - senao, um token emitido pela instancia A e rejeitado pelo
     * jwtDecoder da instancia B (401 espurio atras de um load balancer), e qualquer restart
     * invalida todos os tokens em circulacao. fiscaladapter.seguranca.chave-rsa-jwk (base64 de um
     * JWK RSA em JSON, incluindo a chave privada - gerar uma vez com RSAKey.Builder + toJSONString
     * e guardar em secret manager/variavel de ambiente, nunca commitar) e obrigatoria em
     * homolog/prod (mesmo padrao fail-fast de fiscaladapter.seguranca.chave-criptografia, ver
     * CriptografiaEmRepousoService); em dev, na ausencia dessa configuracao, gera uma chave em
     * memoria a cada start (com o aviso alto de BootstrapClienteDevConfig), aceitavel so porque dev
     * roda com uma unica instancia e reinicios frequentes.
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${fiscaladapter.seguranca.chave-rsa-jwk:}") String chaveRsaJwkBase64) throws Exception {
        RSAKey rsaKey = chaveRsaJwkBase64.isBlank() ? gerarChaveEmMemoria() : carregarChaveDeConfiguracao(chaveRsaJwkBase64);
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private RSAKey gerarChaveEmMemoria() throws Exception {
        log.warn("fiscaladapter.seguranca.chave-rsa-jwk nao configurada - gerando par de chaves RSA em memoria "
                + "para esta instancia. Tokens emitidos NAO sobrevivem a um restart e NAO sao aceitos por outra "
                + "instancia da aplicacao. So aceitavel em desenvolvimento local com uma unica instancia - "
                + "configure a chave persistida antes de homologacao/producao.");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    private RSAKey carregarChaveDeConfiguracao(String chaveRsaJwkBase64) throws Exception {
        String json = new String(Base64.getDecoder().decode(chaveRsaJwkBase64), StandardCharsets.UTF_8);
        return RSAKey.parse(json);
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}
