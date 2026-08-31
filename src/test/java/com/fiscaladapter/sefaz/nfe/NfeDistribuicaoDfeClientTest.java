package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfeDistribuicaoDfeClientTest {

    private static final String CHAVE_ACESSO = "35260012345678000199550010000000421000000010";

    @Test
    void deveConsultarPorNsuEInterpretarResumoDeNfeDestinada() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        String resNfe = "<resNFe xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<chNFe>" + CHAVE_ACESSO + "</chNFe>"
                + "<CNPJ>11222333000181</CNPJ>"
                + "<xNome>Fornecedor Exemplo Ltda</xNome>"
                + "<dhEmi>2026-08-01T10:00:00-03:00</dhEmi>"
                + "<tpNF>1</tpNF>"
                + "<vNF>1500.50</vNF>"
                + "<dhRecbto>2026-08-01T10:05:00-03:00</dhRecbto>"
                + "<cSitNFe>1</cSitNFe>"
                + "</resNFe>";

        String respostaXml = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><nfeDistDFeInteresseResponse><nfeDistDFeInteresseResult>"
                + "<retDistDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<tpAmb>2</tpAmb><cStat>138</cStat><xMotivo>Documento localizado</xMotivo>"
                + "<ultNSU>000000000000123</ultNSU><maxNSU>000000000000123</maxNSU>"
                + "<loteDistDFeInt><docZip NSU=\"000000000000123\" schema=\"resNFe_v1.01.xsd\">"
                + gzipBase64(resNfe) + "</docZip></loteDistDFeInt>"
                + "</retDistDFeInt>"
                + "</nfeDistDFeInteresseResult></nfeDistDFeInteresseResponse></soap12:Body></soap12:Envelope>";

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("nfeDistDFeInteresse").contains("<cUFAutor>35</cUFAutor>")
                    .contains("<CNPJ>18715523000105</CNPJ>").contains("<ultNSU>000000000000000</ultNSU>");
            return respostaXml;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeDistribuicaoDfeClient client = new NfeDistribuicaoDfeClient(null, null);
            RetornoDistribuicaoDfe retorno = client.consultarPorNsu(servidor.url(), "18715523000105", "SP", "0",
                    TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(retorno.sucesso()).isTrue();
            assertThat(retorno.cStat()).isEqualTo("138");
            assertThat(retorno.ultNsu()).isEqualTo("000000000000123");
            assertThat(retorno.maxNsu()).isEqualTo("000000000000123");
            assertThat(retorno.resumos()).hasSize(1);

            ResumoNfeDistribuicao resumo = retorno.resumos().get(0);
            assertThat(resumo.chaveAcesso()).isEqualTo(CHAVE_ACESSO);
            assertThat(resumo.cnpjEmitente()).isEqualTo("11222333000181");
            assertThat(resumo.nomeEmitente()).isEqualTo("Fornecedor Exemplo Ltda");
            assertThat(resumo.valorNota()).isEqualByComparingTo(new BigDecimal("1500.50"));
            assertThat(resumo.dataAutorizacao()).isEqualTo(OffsetDateTime.parse("2026-08-01T10:05:00-03:00"));
            assertThat(resumo.situacao()).isEqualTo(SituacaoNfeDistribuicao.AUTORIZADA);
        }
    }

    @Test
    void deveRetornarListaVaziaQuandoNenhumDocumentoLocalizado() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        String respostaXml = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><nfeDistDFeInteresseResponse><nfeDistDFeInteresseResult>"
                + "<retDistDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<tpAmb>2</tpAmb><cStat>137</cStat><xMotivo>Nenhum documento localizado</xMotivo>"
                + "<ultNSU>000000000000050</ultNSU><maxNSU>000000000000050</maxNSU>"
                + "</retDistDFeInt>"
                + "</nfeDistDFeInteresseResult></nfeDistDFeInteresseResponse></soap12:Body></soap12:Envelope>";

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> respostaXml)) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeDistribuicaoDfeClient client = new NfeDistribuicaoDfeClient(null, null);
            RetornoDistribuicaoDfe retorno = client.consultarPorNsu(servidor.url(), "18715523000105", "SP", "50",
                    TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(retorno.sucesso()).isTrue();
            assertThat(retorno.resumos()).isEmpty();
        }
    }

    @Test
    void deveLancarExcecaoQuandoSefazRecusaPorConsumoIndevido() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        String respostaXml = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><nfeDistDFeInteresseResponse><nfeDistDFeInteresseResult>"
                + "<retDistDFeInt versao=\"1.01\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<tpAmb>2</tpAmb><cStat>656</cStat><xMotivo>Rejeicao: Consumo Indevido</xMotivo>"
                + "</retDistDFeInt>"
                + "</nfeDistDFeInteresseResult></nfeDistDFeInteresseResponse></soap12:Body></soap12:Envelope>";

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> respostaXml)) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeDistribuicaoDfeClient client = new NfeDistribuicaoDfeClient(null, null);
            RetornoDistribuicaoDfe retorno = client.consultarPorNsu(servidor.url(), "18715523000105", "SP", "0",
                    TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(retorno.sucesso()).isFalse();
            assertThat(retorno.cStat()).isEqualTo("656");
        }
    }

    @Test
    void deveLancarExcecaoQuandoUfDesconhecida() {
        NfeDistribuicaoDfeClient client = new NfeDistribuicaoDfeClient(null, null);
        assertThatThrownBy(() -> client.consultarPorNsu("https://exemplo.invalido", "18715523000105", "ZZ", "0",
                TipoAmbiente.HOMOLOGACAO, HttpClient.newHttpClient()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String gzipBase64(String texto) throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(saida)) {
            gzip.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        return Base64.getEncoder().encodeToString(saida.toByteArray());
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("18715523000105", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
