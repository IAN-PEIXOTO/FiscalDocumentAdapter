package com.fiscaladapter.sefaz.nfse;

import com.fiscaladapter.sefaz.SefazComunicacaoException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Envelope SOAP 1.1 do padrao ABRASF: cabecalho (versaoDados) no Header,
 * operacao (GerarNfseEnvio/ConsultarNfseRpsEnvio/CancelarNfseEnvio) no Body -
 * conforme os elementos "cabecalho" e as operacoes definidas no proprio XSD
 * oficial (nfse_v2.01.xsd).
 *
 * Diferente da NFe (uma unica WSDL nacional por servico, com convencao
 * estavel de nfeCabecMsg/nfeDadosMsg/nfeResultMsg), cada prefeitura publica
 * sua propria WSDL para a mesma operacao ABRASF, e o envelope SOAP exato
 * (nomes de elemento do "wrapper" da operacao, presenca de SOAPAction, etc.)
 * pode variar de implementacao para implementacao. Por isso este cliente
 * devolve o corpo cru da resposta (sem tentar "desembrulhar" um elemento
 * especifico como o SoapClient da NFe faz com nfeResultMsg) - cada operacao
 * do AbrasfNfseClient extrai o que precisa da resposta com uma busca
 * tolerante ao restante do envelope.
 */
final class AbrasfSoapClient {

    private AbrasfSoapClient() {
    }

    static String enviar(HttpClient httpClient, String url, String soapAction, String xmlDaOperacao) {
        String envelope = montarEnvelope(xmlDaOperacao);

        try {
            HttpRequest.Builder requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", soapAction)
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8));

            HttpResponse<String> resposta = httpClient.send(requisicao.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() != 200) {
                throw new SefazComunicacaoException(
                        "Webservice de NFS-e retornou HTTP " + resposta.statusCode() + " para " + url + ": " + resposta.body());
            }

            return resposta.body();
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SefazComunicacaoException("Falha de comunicacao com o webservice de NFS-e (" + url + ")", e);
        }
    }

    private static String montarEnvelope(String xmlDaOperacao) {
        return "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Header>"
                + "<cabecalho xmlns=\"http://www.abrasf.org.br/nfse.xsd\" versao=\"2.01\">"
                + "<versaoDados>2.01</versaoDados>"
                + "</cabecalho>"
                + "</soap:Header>"
                + "<soap:Body>"
                + xmlDaOperacao
                + "</soap:Body>"
                + "</soap:Envelope>";
    }
}
