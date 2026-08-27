package com.fiscaladapter.sefaz.nfe;

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

class NfeConsultaProtocoloClientTest {

    private static final String RESPOSTA_AUTORIZADA =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4\">"
                    + "<retConsSitNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>"
                    + "<protNFe versao=\"4.00\"><infProt><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>"
                    + "<nProt>135260000000001</nProt></infProt></protNFe>"
                    + "</retConsSitNFe>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveConsultarProtocoloEInterpretarAutorizacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("consSitNFe").contains("<chNFe>35260012345678000199550010000000421000000010</chNFe>");
            return RESPOSTA_AUTORIZADA;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeConsultaProtocoloClient client = new NfeConsultaProtocoloClient(null, null);
            ConsultaProtocoloResponse resposta = client.consultar(servidor.url(),
                    "35260012345678000199550010000000421000000010", "SP", TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(resposta.autorizada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("135260000000001");
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
