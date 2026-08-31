package com.fiscaladapter.sefaz.cte;

import com.fiscaladapter.sefaz.SefazComunicacaoException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

/**
 * Envelope SOAP 1.2 compartilhado pelos servicos do CT-e 4.00 (FIS-44):
 * cabecalho cteCabecMsg (cUF/versaoDados) + corpo cteDadosMsg - estrutura
 * simetrica ao SoapClient da NFe (nfeCabecMsg/nfeDadosMsg), com uma
 * diferenca real verificada na implementacao de referencia
 * (nfephp-org/sped-cte, Common/Tools.php): a autorizacao (envio do CT-e
 * assinado) exige o conteudo gzip+base64 dentro de cteDadosMsg - consulta e
 * evento (cancelamento) vao em texto puro, sem compressao.
 *
 * Nao tenta extrair um elemento especifico da resposta (a exemplo do
 * nfeResultMsg do SoapClient da NFe): a resposta do CTeRecepcaoSincV4 nao
 * foi confirmada como comprimida (a implementacao de referencia nao
 * descomprime), entao devolve o corpo cru do SOAP Body - cada cliente extrai
 * cStat/xMotivo/protCTe por regex diretamente dele, tolerando variacao no
 * elemento que envolve o resultado.
 */
final class CteSoapClient {

    private CteSoapClient() {
    }

    static String enviarTextoPuro(HttpClient httpClient, String url, String namespace, String cUF,
                                   String versaoDados, String xmlInterno) {
        return enviar(httpClient, url, namespace, cUF, versaoDados, xmlInterno);
    }

    static String enviarComprimido(HttpClient httpClient, String url, String namespace, String cUF,
                                    String versaoDados, String xmlInterno) {
        return enviar(httpClient, url, namespace, cUF, versaoDados, comprimir(xmlInterno));
    }

    private static String enviar(HttpClient httpClient, String url, String namespace, String cUF,
                                  String versaoDados, String conteudoCteDadosMsg) {
        String envelope = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Header>"
                + "<cteCabecMsg xmlns=\"" + namespace + "\">"
                + "<cUF>" + cUF + "</cUF>"
                + "<versaoDados>" + versaoDados + "</versaoDados>"
                + "</cteCabecMsg>"
                + "</soap12:Header>"
                + "<soap12:Body>"
                + "<cteDadosMsg xmlns=\"" + namespace + "\">" + conteudoCteDadosMsg + "</cteDadosMsg>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";

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
            return resposta.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SefazComunicacaoException("Falha de comunicacao com a SEFAZ (" + url + ")", e);
        }
    }

    private static String comprimir(String texto) {
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(saida)) {
                gzip.write(texto.getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getEncoder().encodeToString(saida.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao comprimir XML do CT-e para envio", e);
        }
    }
}
