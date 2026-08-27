package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.sefaz.SefazComunicacaoException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Envelope SOAP 1.2 compartilhado por todos os servicos da NFe 4.00: o
 * corpo sempre envia &lt;nfeDadosMsg&gt; e recebe &lt;nfeResultMsg&gt;,
 * mudando apenas o namespace (especifico de cada WSDL) e o XML interno.
 * Esse padrao e estavel e documentado de forma consistente em todas as
 * implementacoes de referencia (ACBr, nfephp, etc.) para o layout 4.00.
 */
final class SoapClient {

    private SoapClient() {
    }

    static String enviar(HttpClient httpClient, String url, String namespaceServico, String cUF,
                          String versaoDados, String xmlInterno) {
        String envelope = montarEnvelope(namespaceServico, cUF, versaoDados, xmlInterno);

        try {
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/soap+xml; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resposta = httpClient.send(requisicao, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (resposta.statusCode() != 200) {
                throw new SefazComunicacaoException(
                        "SEFAZ retornou HTTP " + resposta.statusCode() + " para " + url + ": " + resposta.body());
            }

            return extrairConteudoResultMsg(resposta.body());
        } catch (java.io.IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SefazComunicacaoException("Falha de comunicacao com a SEFAZ (" + url + ")", e);
        }
    }

    private static String montarEnvelope(String namespaceServico, String cUF, String versaoDados, String xmlInterno) {
        return "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Header>"
                + "<nfeCabecMsg xmlns=\"" + namespaceServico + "\">"
                + "<cUF>" + cUF + "</cUF>"
                + "<versaoDados>" + versaoDados + "</versaoDados>"
                + "</nfeCabecMsg>"
                + "</soap12:Header>"
                + "<soap12:Body>"
                + "<nfeDadosMsg xmlns=\"" + namespaceServico + "\">"
                + xmlInterno
                + "</nfeDadosMsg>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }

    private static String extrairConteudoResultMsg(String respostaSoap) {
        int inicioTag = respostaSoap.indexOf("nfeResultMsg");
        int fimTagFechamento = respostaSoap.indexOf("</" + tagComPrefixo(respostaSoap, inicioTag));
        if (inicioTag < 0 || fimTagFechamento < 0) {
            throw new SefazComunicacaoException("Resposta da SEFAZ nao contem nfeResultMsg: " + respostaSoap);
        }
        int inicioConteudo = respostaSoap.indexOf('>', inicioTag) + 1;
        if (inicioConteudo <= 0 || fimTagFechamento <= inicioConteudo) {
            throw new SefazComunicacaoException("Nao foi possivel extrair o conteudo de nfeResultMsg: " + respostaSoap);
        }
        return respostaSoap.substring(inicioConteudo, fimTagFechamento).trim();
    }

    /** Retorna "nfeResultMsg" com o prefixo de namespace, se houver (ex.: "ns2:nfeResultMsg"), para casar a tag de fechamento. */
    private static String tagComPrefixo(String xml, int posicaoNomeTag) {
        int inicioAbertura = xml.lastIndexOf('<', posicaoNomeTag);
        return xml.substring(inicioAbertura + 1, posicaoNomeTag) + "nfeResultMsg";
    }
}
