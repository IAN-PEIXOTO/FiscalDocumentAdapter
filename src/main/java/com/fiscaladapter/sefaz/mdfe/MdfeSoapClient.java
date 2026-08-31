package com.fiscaladapter.sefaz.mdfe;

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
 * Envelope SOAP 1.2 compartilhado pelos servicos do MDF-e 3.00 (FIS-45):
 * cabecalho mdfeCabecMsg (cUF/versaoDados) + corpo mdfeDadosMsg - mesma
 * estrutura do CteSoapClient (CT-e, FIS-44), verificada contra a
 * implementacao de referencia nfephp-org/sped-mdfe (Common/Tools.php): a
 * autorizacao sincrona (MDFeRecepcaoSinc) exige o conteudo gzip+base64;
 * consulta e evento (encerramento/cancelamento) vao em texto puro.
 *
 * ATENCAO (nao verificavel nesta sessao): uma fonte secundaria (nao a
 * implementacao de referencia usada aqui) sugere que o binding WSDL do
 * MDFeRecepcaoSinc especificamente NAO declara mdfeCabecMsg como parte do
 * header - a implementacao de referencia, porem, envia o header
 * uniformemente para todos os servicos (inclusive o sincrono) e e o que
 * este cliente segue, ja que um header SOAP nao declarado no binding e
 * tipicamente ignorado pelo servidor (sem mustUnderstand), nao rejeitado.
 */
final class MdfeSoapClient {

    private MdfeSoapClient() {
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
                                  String versaoDados, String conteudoMdfeDadosMsg) {
        String envelope = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Header>"
                + "<mdfeCabecMsg xmlns=\"" + namespace + "\">"
                + "<cUF>" + cUF + "</cUF>"
                + "<versaoDados>" + versaoDados + "</versaoDados>"
                + "</mdfeCabecMsg>"
                + "</soap12:Header>"
                + "<soap12:Body>"
                + "<mdfeDadosMsg xmlns=\"" + namespace + "\">" + conteudoMdfeDadosMsg + "</mdfeDadosMsg>"
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
            throw new IllegalStateException("Falha ao comprimir XML do MDF-e para envio", e);
        }
    }
}
