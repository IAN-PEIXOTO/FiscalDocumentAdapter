package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfeCancelamentoClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199550010000000421000000010";

    private static final String RESPOSTA_CANCELAMENTO_HOMOLOGADO =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                    + "<retEnvEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cOrgao>35</cOrgao>"
                    + "<cStat>128</cStat><xMotivo>Lote de Evento Processado</xMotivo>"
                    + "<retEvento versao=\"1.00\"><infEvento><tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cOrgao>35</cOrgao>"
                    + "<cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>"
                    + "<chNFe>" + CHAVE_ACESSO + "</chNFe><tpEvento>110111</tpEvento>"
                    + "<dhRegEvento>2026-03-15T10:00:00-03:00</dhRegEvento></infEvento></retEvento>"
                    + "</retEnvEvento>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveCancelarNfeComEventoAssinadoEInterpretarHomologacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("envEvento").contains("tpEvento>110111").contains("Signature");
            return RESPOSTA_CANCELAMENTO_HOMOLOGADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeCancelamentoClient client = new NfeCancelamentoClient(null, null, new AssinaturaXmlService());
            CancelamentoResponse resposta = client.cancelar(servidor.url(), CHAVE_ACESSO, "135260000000001",
                    "Erro de digitacao no valor do produto identificado apos a emissao", "SP",
                    TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.cancelado()).isTrue();
        }
    }

    @Test
    void deveRejeitarJustificativaCurta() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NfeCancelamentoClient client = new NfeCancelamentoClient(null, null, new AssinaturaXmlService());

        assertThatThrownBy(() -> client.cancelar("url-nao-usada", CHAVE_ACESSO, "135260000000001",
                "curta demais", "SP", TipoAmbiente.HOMOLOGACAO, certificado, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
