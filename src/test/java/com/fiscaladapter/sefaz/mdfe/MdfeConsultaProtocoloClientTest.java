package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MdfeConsultaProtocoloClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199580010000000421000000010";

    private static final String RESPOSTA_AUTORIZADO =
            "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><mdfeConsultaResult>"
                    + "<retConsSitMDFe versao=\"3.00\" xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                    + "<tpAmb>2</tpAmb><cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>"
                    + "<protMDFe versao=\"3.00\"><infProt><cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>"
                    + "<nProt>935260000000001</nProt><dhRecbto>2026-03-15T10:00:00-03:00</dhRecbto></infProt></protMDFe>"
                    + "</retConsSitMDFe>"
                    + "</mdfeConsultaResult></soap12:Body></soap12:Envelope>";

    @Test
    void deveConsultarMdfeEInterpretarAutorizacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("consSitMDFe").contains("<chMDFe>" + CHAVE_ACESSO + "</chMDFe>");
            return RESPOSTA_AUTORIZADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            MdfeConsultaProtocoloClient client = new MdfeConsultaProtocoloClient(null, null);
            ConsultaProtocoloResponse resposta = client.consultar(servidor.url(), CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(resposta.autorizada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("935260000000001");
            assertThat(resposta.dhRecbto()).isEqualTo("2026-03-15T10:00:00-03:00");
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
