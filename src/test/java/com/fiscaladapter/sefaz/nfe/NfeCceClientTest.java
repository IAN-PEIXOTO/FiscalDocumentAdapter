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

class NfeCceClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199550010000000421000000010";

    private static final String RESPOSTA_CCE_REGISTRADA =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                    + "<retEnvEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cOrgao>35</cOrgao>"
                    + "<cStat>128</cStat><xMotivo>Lote de Evento Processado</xMotivo>"
                    + "<retEvento versao=\"1.00\"><infEvento><tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cOrgao>35</cOrgao>"
                    + "<cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>"
                    + "<chNFe>" + CHAVE_ACESSO + "</chNFe><tpEvento>110110</tpEvento><nSeqEvento>1</nSeqEvento>"
                    + "<dhRegEvento>2026-03-15T10:00:00-03:00</dhRegEvento><nProt>135260000000002</nProt>"
                    + "</infEvento></retEvento>"
                    + "</retEnvEvento>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveEmitirCceComEventoAssinadoEInterpretarRegistro() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("envEvento").contains("tpEvento>110110")
                    .contains("Carta de Correção").contains("Signature");
            return RESPOSTA_CCE_REGISTRADA;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeCceClient client = new NfeCceClient(null, null, new AssinaturaXmlService());
            CceResponse resposta = client.corrigir(servidor.url(), CHAVE_ACESSO, 1,
                    "Correcao do endereco de entrega informado na nota, sem alteracao de valores",
                    "SP", TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.registrada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("135260000000002");
        }
    }

    @Test
    void deveRejeitarTextoDeCorrecaoCurto() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NfeCceClient client = new NfeCceClient(null, null, new AssinaturaXmlService());

        assertThatThrownBy(() -> client.corrigir("url-nao-usada", CHAVE_ACESSO, 1, "curto",
                "SP", TipoAmbiente.HOMOLOGACAO, certificado, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarSequencialForaDoIntervalo() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NfeCceClient client = new NfeCceClient(null, null, new AssinaturaXmlService());

        assertThatThrownBy(() -> client.corrigir("url-nao-usada", CHAVE_ACESSO, 21,
                "Correcao valida com mais de quinze caracteres", "SP", TipoAmbiente.HOMOLOGACAO, certificado, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
