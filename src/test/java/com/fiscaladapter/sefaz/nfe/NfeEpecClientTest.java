package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class NfeEpecClientTest {

    private static final String CHAVE_ACESSO = "35260412345678000199550010000000424000000015";

    private static final String RESPOSTA_EPEC_REGISTRADO =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                    + "<retEnvEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>RS1.0</verAplic><cOrgao>91</cOrgao>"
                    + "<cStat>128</cStat><xMotivo>Lote de Evento Processado</xMotivo>"
                    + "<retEvento versao=\"1.00\"><infEvento><tpAmb>2</tpAmb><verAplic>RS1.0</verAplic><cOrgao>91</cOrgao>"
                    + "<cStat>136</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>"
                    + "<chNFe>" + CHAVE_ACESSO + "</chNFe><tpEvento>110140</tpEvento>"
                    + "<dhRegEvento>2026-03-15T10:00:00-03:00</dhRegEvento></infEvento></retEvento>"
                    + "</retEnvEvento>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveRegistrarEpecComEventoAssinadoEInterpretarSucesso() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("envEvento").contains("tpEvento>110140").contains("descEvento>EPEC")
                    .contains("cOrgao>91").contains("Signature");
            return RESPOSTA_EPEC_REGISTRADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeEpecClient client = new NfeEpecClient(null, null, new AssinaturaXmlService());
            EpecResponse resposta = client.registrar(servidor.url(), nfe, CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO,
                    certificado, httpClient);

            assertThat(resposta.registrada()).isTrue();
            assertThat(resposta.codigoStatus()).isEqualTo("136");
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
