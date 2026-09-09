package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FIS-111: uma resposta sem nfeResultMsg (ex.: um SOAP Fault, que alguns webservices de SEFAZ
 * devolvem em vez do envelope nfeResultMsg normal em caso de erro do lado deles) lancava
 * StringIndexOutOfBoundsException em vez de um SefazComunicacaoException informativo - descoberto
 * ao testar contra a SEFAZ-PR de homologacao de verdade.
 *
 * FIS-110: o nome do elemento de retorno dentro de soap:Body nao e sempre "nfeResultMsg" -
 * RecepcaoEvento (EPEC/CC-e/cancelamento/manifestacao) responde num elemento com nome proprio da
 * operacao (ex.: nfeRecepcaoEventoNFResult), tambem descoberto testando EPEC contra a SEFAZ-PR de
 * homologacao de verdade.
 */
class SoapClientTest {

    private HttpServer servidor;

    @AfterEach
    void pararServidor() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void deveLancarErroClaroQuandoRespostaEUmSoapFault() throws Exception {
        String soapFault = "<?xml version=\"1.0\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><soap12:Fault><soap12:Code><soap12:Value>soap12:Receiver</soap12:Value></soap12:Code>"
                + "<soap12:Reason><soap12:Text>Erro interno do servidor</soap12:Text></soap12:Reason>"
                + "</soap12:Fault></soap12:Body></soap12:Envelope>";

        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/servico", exchange -> {
            byte[] corpo = soapFault.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        servidor.start();
        String url = "http://localhost:" + servidor.getAddress().getPort() + "/servico";

        assertThatThrownBy(() -> SoapClient.enviar(HttpClient.newHttpClient(), url,
                "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4", "41", "4.00", "<enviNFe/>"))
                .isInstanceOf(SefazComunicacaoException.class)
                .hasMessageContaining("SOAP Fault");
    }

    /**
     * FIS-110: RecepcaoEvento (EPEC/CC-e/cancelamento/manifestacao) nao responde em
     * &lt;nfeResultMsg&gt; como Autorizacao/Consulta/StatusServico - responde num elemento com
     * nome proprio da operacao. Descoberto contra a SEFAZ-PR de homologacao de verdade, onde o
     * EPEC respondeu em &lt;nfeRecepcaoEventoNFResult&gt;.
     */
    @Test
    void deveExtrairConteudoQuandoElementoDeRetornoNaoSeChamaNfeResultMsg() throws Exception {
        String respostaRecepcaoEvento = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap:Body><nfeRecepcaoEventoNFResult xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                + "<retEnvEvento versao=\"1.00\"><cStat>128</cStat></retEnvEvento>"
                + "</nfeRecepcaoEventoNFResult></soap:Body></soap:Envelope>";

        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/servico", exchange -> {
            byte[] corpo = respostaRecepcaoEvento.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        servidor.start();
        String url = "http://localhost:" + servidor.getAddress().getPort() + "/servico";

        String conteudo = SoapClient.enviar(HttpClient.newHttpClient(), url,
                "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4", "41", "1.00", "<envEvento/>");

        assertThat(conteudo).contains("<retEnvEvento").contains("<cStat>128</cStat>");
    }

    @Test
    void deveExtrairConteudoQuandoRespostaContemNfeResultMsgComPrefixoDeNamespace() throws Exception {
        String respostaComPrefixo = "<?xml version=\"1.0\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body><ns2:nfeResultMsg xmlns:ns2=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">"
                + "<retEnviNFe><cStat>103</cStat></retEnviNFe>"
                + "</ns2:nfeResultMsg></soap12:Body></soap12:Envelope>";

        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/servico", exchange -> {
            byte[] corpo = respostaComPrefixo.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        servidor.start();
        String url = "http://localhost:" + servidor.getAddress().getPort() + "/servico";

        String conteudo = SoapClient.enviar(HttpClient.newHttpClient(), url,
                "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4", "41", "4.00", "<enviNFe/>");

        assertThat(conteudo).contains("<retEnviNFe>").contains("<cStat>103</cStat>");
    }
}
