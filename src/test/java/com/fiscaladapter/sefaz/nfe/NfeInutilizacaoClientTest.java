package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
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

class NfeInutilizacaoClientTest {

    private static final String CNPJ_EMITENTE = "12345678000199";

    private static final String RESPOSTA_INUTILIZACAO_HOMOLOGADA =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4\">"
                    + "<retInutNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<infInut><tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cStat>102</cStat>"
                    + "<xMotivo>Inutilizacao de numero homologada</xMotivo><cUF>35</cUF><ano>26</ano>"
                    + "<CNPJ>" + CNPJ_EMITENTE + "</CNPJ><mod>55</mod><serie>1</serie>"
                    + "<nNFIni>100</nNFIni><nNFFin>110</nNFFin>"
                    + "<dhRecbto>2026-03-15T10:00:00-03:00</dhRecbto><nProt>135260000000003</nProt>"
                    + "</infInut></retInutNFe>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveInutilizarFaixaComInfInutAssinadoEInterpretarHomologacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("inutNFe").contains("xServ>INUTILIZAR").contains("Signature")
                    .contains("<nNFIni>100</nNFIni>").contains("<nNFFin>110</nNFFin>");
            return RESPOSTA_INUTILIZACAO_HOMOLOGADA;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeInutilizacaoClient client = new NfeInutilizacaoClient(null, null, new AssinaturaXmlService(), new ChaveAcessoService());
            InutilizacaoResponse resposta = client.inutilizar(servidor.url(), CNPJ_EMITENTE, "SP", 1, 100, 110,
                    "Numeracao pulada por erro de sistema antes da transmissao", TipoAmbiente.HOMOLOGACAO,
                    certificado, httpClient);

            assertThat(resposta.inutilizada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("135260000000003");
        }
    }

    @Test
    void deveRejeitarJustificativaCurta() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NfeInutilizacaoClient client = new NfeInutilizacaoClient(null, null, new AssinaturaXmlService(), new ChaveAcessoService());

        assertThatThrownBy(() -> client.inutilizar("url-nao-usada", CNPJ_EMITENTE, "SP", 1, 100, 110,
                "curta demais", TipoAmbiente.HOMOLOGACAO, certificado, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarFaixaComNumeroFinalMenorQueInicial() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NfeInutilizacaoClient client = new NfeInutilizacaoClient(null, null, new AssinaturaXmlService(), new ChaveAcessoService());

        assertThatThrownBy(() -> client.inutilizar("url-nao-usada", CNPJ_EMITENTE, "SP", 1, 110, 100,
                "Numeracao pulada por erro de sistema antes da transmissao", TipoAmbiente.HOMOLOGACAO,
                certificado, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMITENTE, senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
