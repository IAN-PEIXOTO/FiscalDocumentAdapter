package com.fiscaladapter.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIS-97: a chave RSA do Authorization Server precisa ser a mesma entre instancias da aplicacao -
 * senao um token emitido por uma instancia e rejeitado pelo jwtDecoder de outra, e qualquer
 * restart invalida todos os tokens em circulacao.
 */
class AuthorizationServerConfigTest {

    private final AuthorizationServerConfig config = new AuthorizationServerConfig();

    @Test
    void deveUsarAMesmaChaveEntreInstanciasQuandoConfiguradaExplicitamente() throws Exception {
        // simula duas instancias da aplicacao lendo a MESMA variavel de ambiente/secret
        String chaveRsaJwkBase64 = gerarChaveRsaJwkBase64();

        JWKSet chaveInstanciaA = extrairJwkSet(config.jwkSource(chaveRsaJwkBase64));
        JWKSet chaveInstanciaB = extrairJwkSet(config.jwkSource(chaveRsaJwkBase64));

        assertThat(chaveInstanciaA.getKeys()).hasSize(1);
        assertThat(chaveInstanciaA.getKeys().get(0).toJSONString())
                .isEqualTo(chaveInstanciaB.getKeys().get(0).toJSONString());
    }

    @Test
    void deveGerarChavesDiferentesEntreInstanciasQuandoNaoConfigurada() throws Exception {
        // reproduz o bug original: sem a configuracao, cada instancia (ou cada restart) gera a sua
        // propria chave - um token emitido por uma nao seria aceito pela outra.
        JWKSet chaveInstanciaA = extrairJwkSet(config.jwkSource(""));
        JWKSet chaveInstanciaB = extrairJwkSet(config.jwkSource(""));

        assertThat(chaveInstanciaA.getKeys().get(0).toJSONString())
                .isNotEqualTo(chaveInstanciaB.getKeys().get(0).toJSONString());
    }

    @SuppressWarnings("unchecked")
    private JWKSet extrairJwkSet(JWKSource<SecurityContext> jwkSource) {
        return ((ImmutableJWKSet<SecurityContext>) jwkSource).getJWKSet();
    }

    private String gerarChaveRsaJwkBase64() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return Base64.getEncoder().encodeToString(rsaKey.toJSONString().getBytes(StandardCharsets.UTF_8));
    }
}
