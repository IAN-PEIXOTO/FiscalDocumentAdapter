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

/**
 * Teste de ponta a ponta contra um servidor HTTPS local com mTLS, ja que o
 * ambiente de homologacao real da SEFAZ nao e acessivel nesta sessao. Prova
 * que o handshake mTLS (certificado do cliente apresentado corretamente) e o
 * envelope SOAP (envio + extracao de nfeResultMsg) funcionam de ponta a
 * ponta - a unica coisa que nao da pra verificar aqui e se o endpoint real
 * da SEFAZ aceita exatamente este formato (ver TODO em NfeAutorizacaoClient
 * sobre validar contra homologacao antes de ir para producao).
 */
class NfeStatusServicoClientTest {

    private static final String RESPOSTA_STATUS_OPERACIONAL =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4\">"
                    + "<retConsStatServ versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<tpAmb>2</tpAmb><cStat>107</cStat><xMotivo>Servico em Operacao</xMotivo>"
                    + "</retConsStatServ>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveConsultarStatusDeServicoDeVerdadeComMtlsEExtrairResultado() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(requisicaoRecebida -> {
            assertThat(requisicaoRecebida).contains("consStatServ").contains("<xServ>STATUS</xServ>");
            return RESPOSTA_STATUS_OPERACIONAL;
        })) {
            SefazHttpClientFactory factory = new SefazHttpClientFactory();
            HttpClient httpClient = factory.criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeStatusServicoClient client = new NfeStatusServicoClient(null, null);
            StatusServicoResponse resposta = client.consultar(servidor.url(), "SP", TipoAmbiente.HOMOLOGACAO, httpClient);

            assertThat(resposta.codigoStatus()).isEqualTo("107");
            assertThat(resposta.servicoEmOperacao()).isTrue();
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
