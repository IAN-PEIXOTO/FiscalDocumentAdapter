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

class NfeManifestacaoDestinatarioClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199550010000000421000000010";

    private static final String RESPOSTA_MANIFESTACAO_REGISTRADA =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                    + "<retEnvEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>AN1.0</verAplic><cOrgao>91</cOrgao>"
                    + "<cStat>128</cStat><xMotivo>Lote de Evento Processado</xMotivo>"
                    + "<retEvento versao=\"1.00\"><infEvento><tpAmb>2</tpAmb><verAplic>AN1.0</verAplic><cOrgao>91</cOrgao>"
                    + "<cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>"
                    + "<chNFe>" + CHAVE_ACESSO + "</chNFe><tpEvento>210210</tpEvento>"
                    + "<nProt>135260000000004</nProt>"
                    + "<dhRegEvento>2026-03-15T10:00:00-03:00</dhRegEvento></infEvento></retEvento>"
                    + "</retEnvEvento>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveManifestarCienciaComEventoAssinadoEInterpretarSucesso() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("envEvento").contains("tpEvento>210210")
                    .contains("descEvento>Ciencia da Operacao").contains("Signature");
            return RESPOSTA_MANIFESTACAO_REGISTRADA;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeManifestacaoDestinatarioClient client =
                    new NfeManifestacaoDestinatarioClient(null, null, new AssinaturaXmlService());
            ManifestacaoResponse resposta = client.manifestar(servidor.url(), CHAVE_ACESSO,
                    TipoManifestacaoDestinatario.CIENCIA_DA_OPERACAO, null, TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.registrada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("135260000000004");
        }
    }

    @Test
    void deveManifestarOperacaoNaoRealizadaComJustificativa() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("tpEvento>210240").contains("descEvento>Operacao nao Realizada")
                    .contains("xJust>Mercadoria recusada por avaria no transporte");
            return RESPOSTA_MANIFESTACAO_REGISTRADA;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeManifestacaoDestinatarioClient client =
                    new NfeManifestacaoDestinatarioClient(null, null, new AssinaturaXmlService());
            client.manifestar(servidor.url(), CHAVE_ACESSO, TipoManifestacaoDestinatario.OPERACAO_NAO_REALIZADA,
                    "Mercadoria recusada por avaria no transporte", TipoAmbiente.HOMOLOGACAO, certificado, httpClient);
        }
    }

    @Test
    void deveRejeitarOperacaoNaoRealizadaSemJustificativaSuficiente() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NfeManifestacaoDestinatarioClient client =
                new NfeManifestacaoDestinatarioClient(null, null, new AssinaturaXmlService());

        assertThatThrownBy(() -> client.manifestar("url-nao-usada", CHAVE_ACESSO,
                TipoManifestacaoDestinatario.OPERACAO_NAO_REALIZADA, "curta", TipoAmbiente.HOMOLOGACAO, certificado, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void naoDeveExigirJustificativaParaCiencia() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> RESPOSTA_MANIFESTACAO_REGISTRADA)) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeManifestacaoDestinatarioClient client =
                    new NfeManifestacaoDestinatarioClient(null, null, new AssinaturaXmlService());

            ManifestacaoResponse resposta = client.manifestar(servidor.url(), CHAVE_ACESSO,
                    TipoManifestacaoDestinatario.CIENCIA_DA_OPERACAO, null, TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.registrada()).isTrue();
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("98765432000188", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
