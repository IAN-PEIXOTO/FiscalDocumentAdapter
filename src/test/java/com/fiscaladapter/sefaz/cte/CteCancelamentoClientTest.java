package com.fiscaladapter.sefaz.cte;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CteCancelamentoClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199570010000000421000000010";

    private static final String RESPOSTA_CANCELADO =
            "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><cteRecepcaoEventoResult>"
                    + "<retEventoCTe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/cte\">"
                    + "<infEvento><cStat>135</cStat><xMotivo>Evento registrado e vinculado ao CT-e</xMotivo>"
                    + "<chCTe>" + CHAVE_ACESSO + "</chCTe><tpEvento>110111</tpEvento>"
                    + "<nProt>135260000000004</nProt></infEvento></retEventoCTe>"
                    + "</cteRecepcaoEventoResult></soap12:Body></soap12:Envelope>";

    @Test
    void deveCancelarComEventoAssinadoEInterpretarSucesso() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("eventoCTe").contains("tpEvento>110111")
                    .contains("evCancCTe").contains("Signature");
            return RESPOSTA_CANCELADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            CteCancelamentoClient client = new CteCancelamentoClient(null, null, new com.fiscaladapter.assinatura.AssinaturaXmlService());
            CancelamentoResponse resposta = client.cancelar(servidor.url(), CHAVE_ACESSO, "135260000000001",
                    "Erro na contratacao do servico de transporte", "SP", TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.cancelado()).isTrue();
        }
    }

    /** FIS-58: numeroProtocolo nao pode contrabandear marcacao dentro do evento assinado. */
    @Test
    void deveEscaparNumeroProtocoloAoCancelar() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        String protocoloMalicioso = "135260000000001</nProt><Injetado>x</Injetado><nProt>2";

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).doesNotContain("<Injetado>");
            assertThat(req).contains("&lt;Injetado&gt;");
            return RESPOSTA_CANCELADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            CteCancelamentoClient client = new CteCancelamentoClient(null, null, new com.fiscaladapter.assinatura.AssinaturaXmlService());
            CancelamentoResponse resposta = client.cancelar(servidor.url(), CHAVE_ACESSO, protocoloMalicioso,
                    "Erro na contratacao do servico de transporte", "SP", TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.cancelado()).isTrue();
        }
    }

    @Test
    void deveRejeitarJustificativaCurta() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        CteCancelamentoClient client = new CteCancelamentoClient(null, null, new com.fiscaladapter.assinatura.AssinaturaXmlService());

        assertThatThrownBy(() -> client.cancelar("https://exemplo.invalido", CHAVE_ACESSO, "135260000000001",
                "curta", "SP", TipoAmbiente.HOMOLOGACAO, certificado, HttpClient.newHttpClient()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
