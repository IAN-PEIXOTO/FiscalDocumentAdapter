package com.fiscaladapter.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova o bloqueio de SSRF no cadastro de webhook (FIS-68) - usa enderecos IP literais (nunca um
 * hostname que exigiria resolucao DNS de verdade) para o teste funcionar offline/hermetico.
 */
class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator();

    @Test
    void deveRejeitarLoopback() {
        assertThatThrownBy(() -> validator.validar("http://127.0.0.1/webhook"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarEnderecoDeMetadadosDeNuvem() {
        assertThatThrownBy(() -> validator.validar("http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarRedePrivadaRfc1918() {
        assertThatThrownBy(() -> validator.validar("http://10.0.0.5/hook")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validar("http://192.168.1.10/hook")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validar("http://172.16.0.1/hook")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void devePermitirEnderecoIpPublico() {
        assertThatCode(() -> validator.validar("https://8.8.8.8/hook")).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarUrlSemHost() {
        assertThatThrownBy(() -> validator.validar("https:///sem-host")).isInstanceOf(IllegalArgumentException.class);
    }
}
