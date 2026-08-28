package com.fiscaladapter.sefaz.nfse;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.documento.nfse.NfseTestFixture;
import com.fiscaladapter.documento.nfse.abrasf.AbrasfNfseXmlGenerator;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class AbrasfNfseClientTest {

    private static final String CNPJ_PRESTADOR = "12345678000199";

    private static final String RESPOSTA_GERACAO_AUTORIZADA =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                    + "<GerarNfseResposta xmlns=\"http://www.abrasf.org.br/nfse.xsd\">"
                    + "<ListaNfse><CompNfse><Nfse><InfNfse>"
                    + "<Numero>987654321</Numero><CodigoVerificacao>ABC12345</CodigoVerificacao>"
                    + "</InfNfse></Nfse></CompNfse></ListaNfse>"
                    + "</GerarNfseResposta>"
                    + "</soap:Body></soap:Envelope>";

    private static final String RESPOSTA_GERACAO_COM_ERRO =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                    + "<GerarNfseResposta xmlns=\"http://www.abrasf.org.br/nfse.xsd\">"
                    + "<ListaMensagemRetorno><MensagemRetorno>"
                    + "<Codigo>E0001</Codigo><Mensagem>CNPJ do prestador nao cadastrado no municipio</Mensagem>"
                    + "</MensagemRetorno></ListaMensagemRetorno>"
                    + "</GerarNfseResposta>"
                    + "</soap:Body></soap:Envelope>";

    private static final String RESPOSTA_CANCELAMENTO_CONFIRMADO =
            "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>"
                    + "<CancelarNfseResposta xmlns=\"http://www.abrasf.org.br/nfse.xsd\">"
                    + "<RetCancelamento><NfseCancelamento versao=\"2.01\"><Confirmacao>"
                    + "<DataHora>2026-03-20T10:00:00</DataHora>"
                    + "</Confirmacao></NfseCancelamento></RetCancelamento>"
                    + "</CancelarNfseResposta>"
                    + "</soap:Body></soap:Envelope>";

    @Test
    void deveGerarNfseEInterpretarAutorizacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        Nfse nfse = NfseTestFixture.nfseDeExemplo();
        String rpsXml = new AbrasfNfseXmlGenerator().gerar(nfse);

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("GerarNfseEnvio").contains("cabecalho").contains(CNPJ_PRESTADOR);
            return RESPOSTA_GERACAO_AUTORIZADA;
        })) {
            HttpClient httpClient = clienteHttp(certificado, servidor);
            AbrasfNfseClient client = new AbrasfNfseClient(null, null);

            NfseResponse resposta = client.gerarNfseNoEndpoint(servidor.url(), rpsXml, httpClient);

            assertThat(resposta.autorizada()).isTrue();
            assertThat(resposta.numeroNfse()).isEqualTo("987654321");
            assertThat(resposta.codigoVerificacao()).isEqualTo("ABC12345");
        }
    }

    @Test
    void deveInterpretarErroNaGeracaoDaNfse() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> RESPOSTA_GERACAO_COM_ERRO)) {
            HttpClient httpClient = clienteHttp(certificado, servidor);
            AbrasfNfseClient client = new AbrasfNfseClient(null, null);

            NfseResponse resposta = client.gerarNfseNoEndpoint(servidor.url(), "<GerarNfseEnvio/>", httpClient);

            assertThat(resposta.autorizada()).isFalse();
            assertThat(resposta.codigoErro()).isEqualTo("E0001");
            assertThat(resposta.mensagemErro()).isEqualTo("CNPJ do prestador nao cadastrado no municipio");
        }
    }

    @Test
    void deveConsultarNfseRpsEInterpretarAutorizacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("ConsultarNfseRpsEnvio").contains("<Numero>42</Numero>").contains(CNPJ_PRESTADOR);
            return RESPOSTA_GERACAO_AUTORIZADA;
        })) {
            HttpClient httpClient = clienteHttp(certificado, servidor);
            AbrasfNfseClient client = new AbrasfNfseClient(null, null);

            NfseResponse resposta = client.consultarNfseRpsNoEndpoint(servidor.url(), 42, "1", CNPJ_PRESTADOR, "123456", httpClient);

            assertThat(resposta.autorizada()).isTrue();
            assertThat(resposta.numeroNfse()).isEqualTo("987654321");
        }
    }

    @Test
    void deveCancelarNfseEInterpretarConfirmacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("CancelarNfseEnvio").contains("<Numero>987654321</Numero>");
            return RESPOSTA_CANCELAMENTO_CONFIRMADO;
        })) {
            HttpClient httpClient = clienteHttp(certificado, servidor);
            AbrasfNfseClient client = new AbrasfNfseClient(null, null);

            CancelamentoNfseResponse resposta = client.cancelarNfseNoEndpoint(
                    servidor.url(), "987654321", CNPJ_PRESTADOR, "123456", "3550308", httpClient);

            assertThat(resposta.cancelada()).isTrue();
            assertThat(resposta.dataHoraCancelamento()).isEqualTo("2026-03-20T10:00:00");
        }
    }

    private HttpClient clienteHttp(CertificadoCarregado certificado, ServidorSoapDeTeste servidor) {
        return new SefazHttpClientFactory().criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_PRESTADOR, senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
