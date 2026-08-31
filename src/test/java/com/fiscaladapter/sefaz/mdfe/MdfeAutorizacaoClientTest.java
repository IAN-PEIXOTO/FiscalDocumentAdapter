package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.mdfe.Mdfe;
import com.fiscaladapter.documento.mdfe.MdfeTestFixture;
import com.fiscaladapter.documento.mdfe.MdfeXmlGenerator;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class MdfeAutorizacaoClientTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final MdfeXmlGenerator xmlGenerator = new MdfeXmlGenerator(chaveAcessoService);
    private final AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();

    private static final String RESPOSTA_AUTORIZADA =
            "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap12:Body><mdfeRecepcaoSincResult>"
                    + "<protMDFe versao=\"3.00\" xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                    + "<infProt><tpAmb>2</tpAmb><verAplic>SVRS1.0</verAplic>"
                    + "<cStat>100</cStat><xMotivo>Autorizado o uso do MDF-e</xMotivo>"
                    + "<nProt>935260000000001</nProt><dhRecbto>2026-03-15T10:00:00-03:00</dhRecbto>"
                    + "</infProt></protMDFe>"
                    + "</mdfeRecepcaoSincResult></soap12:Body></soap12:Envelope>";

    @Test
    void deveEnviarMdfeComprimidoEInterpretarAutorizacao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        String mdfeXmlAssinado = mdfeAssinadoDeExemplo(certificado);

        StringBuilder requisicaoCapturada = new StringBuilder();
        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            requisicaoCapturada.append(req);
            return RESPOSTA_AUTORIZADA;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            MdfeAutorizacaoClient client = new MdfeAutorizacaoClient(null, null);
            AutorizacaoResponse resposta = client.autorizar(servidor.url(), mdfeXmlAssinado, "SP", httpClient);

            assertThat(resposta.autorizada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("935260000000001");

            assertThat(requisicaoCapturada.toString())
                    .contains("mdfeCabecMsg").contains("<cUF>35</cUF>").contains("mdfeDadosMsg");

            String conteudoComprimido = extrairConteudoTag(requisicaoCapturada.toString(), "mdfeDadosMsg");
            String xmlDescompactado = descompactar(conteudoComprimido);
            assertThat(xmlDescompactado).contains("infMDFe").contains("Signature");
        }
    }

    @Test
    void deveInterpretarRejeicao() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        String mdfeXmlAssinado = mdfeAssinadoDeExemplo(certificado);

        String respostaRejeitada =
                "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                        + "<soap12:Body><mdfeRecepcaoSincResult>"
                        + "<cStat>225</cStat><xMotivo>Rejeicao: Falha no Schema XML</xMotivo>"
                        + "</mdfeRecepcaoSincResult></soap12:Body></soap12:Envelope>";

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> respostaRejeitada)) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            MdfeAutorizacaoClient client = new MdfeAutorizacaoClient(null, null);
            AutorizacaoResponse resposta = client.autorizar(servidor.url(), mdfeXmlAssinado, "SP", httpClient);

            assertThat(resposta.autorizada()).isFalse();
            assertThat(resposta.codigoStatus()).isEqualTo("225");
        }
    }

    private String mdfeAssinadoDeExemplo(CertificadoCarregado certificado) {
        Mdfe mdfe = MdfeTestFixture.mdfeDeExemplo();
        String chaveAcesso = chaveAcessoService.gerar(mdfe.identificacao().uf(), mdfe.identificacao().dataEmissao(),
                mdfe.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.MDFE),
                mdfe.identificacao().serie(), mdfe.identificacao().numero(), 1);
        String xmlSemAssinatura = xmlGenerator.gerar(mdfe, chaveAcesso);
        return assinaturaXmlService.assinar(xmlSemAssinatura, "MDFe" + chaveAcesso, certificado);
    }

    private String extrairConteudoTag(String xml, String tag) {
        int inicioAbertura = xml.indexOf("<" + tag);
        int inicioConteudo = xml.indexOf('>', inicioAbertura) + 1;
        int fim = xml.indexOf("</" + tag, inicioConteudo);
        return xml.substring(inicioConteudo, fim);
    }

    private String descompactar(String base64Gzip) throws Exception {
        byte[] comprimido = Base64.getDecoder().decode(base64Gzip);
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido));
             ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            gzip.transferTo(saida);
            return saida.toString(StandardCharsets.UTF_8);
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
