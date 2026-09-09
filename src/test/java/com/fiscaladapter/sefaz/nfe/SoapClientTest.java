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
 * FIS-110: uma resposta sem nfeResultMsg (ex.: um SOAP Fault, que alguns webservices de SEFAZ
 * devolvem em vez do envelope nfeResultMsg normal em caso de erro do lado deles) lancava
 * StringIndexOutOfBoundsException em vez de um SefazComunicacaoException informativo - descoberto
 * ao testar contra a SEFAZ-PR de homologacao de verdade.
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
    void deveLancarErroClaroQuandoRespostaNaoContemNfeResultMsg() throws Exception {
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
                .hasMessageContaining("nao contem nfeResultMsg");
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
