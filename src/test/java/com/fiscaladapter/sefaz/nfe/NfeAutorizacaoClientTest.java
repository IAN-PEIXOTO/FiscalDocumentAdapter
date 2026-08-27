package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
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

class NfeAutorizacaoClientTest {

    private static final String RESPOSTA_LOTE_AUTORIZADO =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">"
                    + "<retEnviNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cStat>103</cStat><xMotivo>Lote recebido com sucesso</xMotivo>"
                    + "<protNFe versao=\"4.00\"><infProt><tpAmb>2</tpAmb><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>"
                    + "<nProt>135260000000001</nProt></infProt></protNFe>"
                    + "</retEnviNFe>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveAutorizarNfeEExtrairProtocoloDoLoteSincrono() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        String xmlAssinado = gerarNfeAssinada(certificado);

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("enviNFe").contains("<indSinc>1</indSinc>").contains("infNFe");
            return RESPOSTA_LOTE_AUTORIZADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeAutorizacaoClient client = new NfeAutorizacaoClient(null, null);
            AutorizacaoResponse resposta = client.autorizarNoEndpoint(servidor.url(), xmlAssinado, "SP", TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(resposta.autorizada()).isTrue();
            assertThat(resposta.numeroProtocolo()).isEqualTo("135260000000001");
        }
    }

    private String gerarNfeAssinada(CertificadoCarregado certificado) {
        ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "55", 1, 42, 1, "10000001");
        String xmlSemAssinatura = new NfeXmlGenerator(chaveAcessoService).gerar(NotaFiscalEletronicaTestFixture.notaDeExemplo(), chave);
        return new com.fiscaladapter.assinatura.AssinaturaXmlService().assinar(xmlSemAssinatura, "NFe" + chave, certificado);
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
