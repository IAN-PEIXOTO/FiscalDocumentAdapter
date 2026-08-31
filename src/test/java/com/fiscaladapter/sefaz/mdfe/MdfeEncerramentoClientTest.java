package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.mdfe.MdfeEncerramentoXmlGenerator;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class MdfeEncerramentoClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199580010000000421000000010";

    private static final String RESPOSTA_ENCERRADO =
            "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><mdfeRecepcaoEventoResult>"
                    + "<retEventoMDFe versao=\"3.00\" xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                    + "<infEvento><cStat>135</cStat><xMotivo>Evento registrado e vinculado ao MDF-e</xMotivo>"
                    + "<chMDFe>" + CHAVE_ACESSO + "</chMDFe><tpEvento>110112</tpEvento>"
                    + "<nProt>935260000000005</nProt></infEvento></retEventoMDFe>"
                    + "</mdfeRecepcaoEventoResult></soap12:Body></soap12:Envelope>";

    @Test
    void deveEncerrarComEventoAssinadoEInterpretarSucesso() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("eventoMDFe").contains("tpEvento>110112")
                    .contains("evEncMDFe").contains("Signature");
            return RESPOSTA_ENCERRADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            MdfeEncerramentoClient client = new MdfeEncerramentoClient(null, null,
                    new AssinaturaXmlService(), new MdfeEncerramentoXmlGenerator());

            var resposta = client.encerrar(servidor.url(), CHAVE_ACESSO, "935260000000001", "SP", "3550308",
                    LocalDate.of(2026, 3, 20), TipoAmbiente.HOMOLOGACAO, certificado, httpClient);

            assertThat(resposta.encerrado()).isTrue();
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
