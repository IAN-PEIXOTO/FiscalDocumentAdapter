package com.fiscaladapter.certificado;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificadoDigitalServiceTest {

    private final CertificadoDigitalService service = new CertificadoDigitalService();

    @Test
    void deveCarregarCertificadoValidoEExtrairCnpj() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199",
                senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365)))
        );

        CertificadoCarregado carregado = service.carregar(TestCertificadoFactory.comoStream(p12), senha);

        assertThat(carregado.info().cnpj()).isEqualTo("12345678000199");
        assertThat(carregado.chaveEEntidade().getPrivateKey()).isNotNull();
        assertThat(carregado.info().expirado(Instant.now())).isFalse();
    }

    /**
     * FIS-110: certificados e-CNPJ REAIS codificam o CNPJ como "otherName" na extensao Subject
     * Alternative Name, nao como RDN do Subject DN (formato que so o certificado sintetico de
     * {@link #deveCarregarCertificadoValidoEExtrairCnpj} usa) - confirmado ao testar contra um
     * certificado real, que devolvia CNPJ null antes desta correcao.
     */
    @Test
    void deveExtrairCnpjDoSubjectAlternativeNameQuandoNaoEstiverNoSubjectDn() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12ComCnpjNoSubjectAlternativeName(
                "09116326000153",
                senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365)))
        );

        CertificadoCarregado carregado = service.carregar(TestCertificadoFactory.comoStream(p12), senha);

        assertThat(carregado.info().cnpj()).isEqualTo("09116326000153");
    }

    @Test
    void deveRejeitarSenhaIncorreta() throws Exception {
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199",
                "senhaCorreta".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365)))
        );

        assertThatThrownBy(() ->
                service.carregar(TestCertificadoFactory.comoStream(p12), "senhaErrada".toCharArray())
        ).isInstanceOf(CertificadoInvalidoException.class);
    }

    @Test
    void deveRejeitarCertificadoExpirado() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199",
                senha,
                Date.from(Instant.now().minus(Duration.ofDays(400))),
                Date.from(Instant.now().minus(Duration.ofDays(1)))
        );

        assertThatThrownBy(() ->
                service.carregar(TestCertificadoFactory.comoStream(p12), senha)
        ).isInstanceOf(CertificadoInvalidoException.class)
                .hasMessageContaining("expirado");
    }
}
