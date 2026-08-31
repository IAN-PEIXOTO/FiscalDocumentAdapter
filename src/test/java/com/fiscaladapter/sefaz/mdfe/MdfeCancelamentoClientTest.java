package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
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

class MdfeCancelamentoClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199580010000000421000000010";

    private static final String RESPOSTA_CANCELADO =
            "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><mdfeRecepcaoEventoResult>"
                    + "<retEventoMDFe versao=\"3.00\" xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                    + "<infEvento><cStat>135</cStat><xMotivo>Evento registrado e vinculado ao MDF-e</xMotivo>"
                    + "<chMDFe>" + CHAVE_ACESSO + "</chMDFe><tpEvento>110111</tpEvento>"
                    + "<nProt>935260000000004</nProt></infEvento></retEventoMDFe>"
                    + "</mdfeRecepcaoEventoResult></soap12:Body></soap12:Envelope>";

    @Test
    void deveCancelarComEventoAssinadoEInterpretarSucesso() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("eventoMDFe").contains("tpEvento>110111")
                    .contains("evCancMDFe").contains("Signature");
            return RESPOSTA_CANCELADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            MdfeCancelamentoClient client = new MdfeCancelamentoClient(null, null, new AssinaturaXmlService());
            CancelamentoResponse resposta = client.cancelar(servidor.url(), CHAVE_ACESSO, "935260000000001",
                    "Erro no cadastro do veiculo de transporte", "SP", TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.cancelado()).isTrue();
        }
    }

    @Test
    void deveRejeitarJustificativaCurta() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        MdfeCancelamentoClient client = new MdfeCancelamentoClient(null, null, new AssinaturaXmlService());

        assertThatThrownBy(() -> client.cancelar("https://exemplo.invalido", CHAVE_ACESSO, "935260000000001",
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
