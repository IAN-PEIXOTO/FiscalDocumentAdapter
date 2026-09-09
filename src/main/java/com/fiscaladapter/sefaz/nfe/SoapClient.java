package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.sefaz.SefazComunicacaoException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Envelope SOAP 1.2 compartilhado por todos os servicos da NFe 4.00: o corpo
 * sempre envia &lt;nfeDadosMsg&gt;. A resposta, porem, NAO usa sempre o mesmo
 * nome de elemento de retorno (FIS-110, descoberto ao testar EPEC contra a
 * SEFAZ-PR de homologacao de verdade): Autorizacao/Consulta/StatusServico
 * respondem em &lt;nfeResultMsg&gt;, mas RecepcaoEvento (usado por EPEC,
 * CC-e, cancelamento e manifestacao do destinatario) responde num elemento
 * com nome proprio da operacao (ex.: &lt;nfeRecepcaoEventoNFResult&gt;) - a
 * suposicao anterior de um nome fixo fazia extrairConteudoResultMsg falhar
 * sempre para essas operacoes. A extracao agora le o (unico) elemento filho
 * de soap:Body, seja qual for o nome dele.
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
        // FIS-111: precisa checar ausencia ANTES de indexar a tag de fechamento - com a tag
        // ausente (ex.: a SEFAZ devolveu um SOAP Fault em vez da resposta normal), indexar direto
        // lancava StringIndexOutOfBoundsException em vez do SefazComunicacaoException informativo.
        int indiceFimAberturaBody = respostaSoap.indexOf("Body>");
        if (indiceFimAberturaBody < 0) {
            throw new SefazComunicacaoException("Resposta da SEFAZ sem soap:Body: " + respostaSoap);
        }
        int inicioTag = respostaSoap.indexOf('<', indiceFimAberturaBody + "Body>".length());
        if (inicioTag < 0) {
            throw new SefazComunicacaoException("Corpo da resposta da SEFAZ esta vazio: " + respostaSoap);
        }

        // FIS-110: o nome do elemento de retorno dentro de soap:Body varia por operacao (ver
        // javadoc da classe) - em vez de assumir "nfeResultMsg", le o nome de fato do (unico)
        // elemento filho do Body. Um SOAP Fault tambem cai aqui (nome de elemento "Fault", com ou
        // sem prefixo) - tratado a parte, com uma mensagem mais especifica.
        String tagCompleta = nomeCompletoDaTag(respostaSoap, inicioTag);
        if (tagCompleta.equals("Fault") || tagCompleta.endsWith(":Fault")) {
            throw new SefazComunicacaoException("Resposta da SEFAZ e um SOAP Fault: " + respostaSoap);
        }

        int inicioConteudo = respostaSoap.indexOf('>', inicioTag) + 1;
        int fimTagFechamento = respostaSoap.indexOf("</" + tagCompleta + ">", inicioConteudo);
        if (inicioConteudo <= 0 || fimTagFechamento < 0) {
            throw new SefazComunicacaoException(
                    "Nao foi possivel extrair o conteudo de <" + tagCompleta + "> da resposta da SEFAZ: " + respostaSoap);
        }
        return respostaSoap.substring(inicioConteudo, fimTagFechamento).trim();
    }

    /** Nome da tag de abertura em "posicaoAbertura", com prefixo de namespace se houver (ex.: "ns2:nfeResultMsg"). */
    private static String nomeCompletoDaTag(String xml, int posicaoAbertura) {
        int fim = posicaoAbertura + 1;
        while (fim < xml.length() && xml.charAt(fim) != ' ' && xml.charAt(fim) != '>' && xml.charAt(fim) != '/') {
            fim++;
        }
        return xml.substring(posicaoAbertura + 1, fim);
    }
}
