package com.fiscaladapter;

import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIS-105: garante um cache de resolucao DNS positivo explicito, em vez de depender do default
 * interno nao documentado da JVM (networkaddress.cache.ttl vem comentado no java.security do JDK).
 * Sem isso, duas resolucoes do mesmo hostname feitas com poucos milissegundos de diferenca (a
 * validacao de SSRF do webhook e a conexao HTTP real logo em seguida, ver
 * WebhookUrlValidator/WebhookNotifierService) poderiam, em tese, bater em DNS diferente a cada
 * chamada - a base da janela de DNS rebinding que a revalidacao por tentativa do FIS-90 sozinha
 * nao fecha.
 */
class FiscalDocumentAdapterApplicationDnsCacheTest {

    @Test
    void deveFixarCacheDeDnsPositivoEmVezDeDependerDoDefaultDaJvm() {
        FiscalDocumentAdapterApplication.fixarCacheDeDnsPositivo();

        assertThat(Security.getProperty("networkaddress.cache.ttl")).isEqualTo("30");
    }
}
