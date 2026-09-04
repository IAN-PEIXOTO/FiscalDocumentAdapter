package com.fiscaladapter.sefaz;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova o cache de HttpClient por certificado (FIS-78) - evita reconstruir o SSLContext/KeyStore
 * a cada chamada SOAP dentro da mesma operacao.
 */
class SefazHttpClientFactoryTest {

    private final SefazHttpClientFactory factory = new SefazHttpClientFactory();

    @Test
    void deveReaproveitarOMesmoHttpClientParaOMesmoCertificado() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        HttpClient primeiro = factory.criar(certificado);
        HttpClient segundo = factory.criar(certificado);

        assertThat(segundo).isSameAs(primeiro);
    }

    @Test
    void deveMontarHttpClientsDiferentesParaCertificadosDiferentes() throws Exception {
        CertificadoCarregado certificadoA = certificadoDeTeste();
        CertificadoCarregado certificadoB = certificadoDeTeste();

        HttpClient clienteA = factory.criar(certificadoA);
        HttpClient clienteB = factory.criar(certificadoB);

        assertThat(clienteA).isNotSameAs(clienteB);
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
