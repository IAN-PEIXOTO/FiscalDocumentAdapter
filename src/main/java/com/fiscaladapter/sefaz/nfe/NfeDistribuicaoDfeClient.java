package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Consulta NF-e destinadas a um CNPJ que ele nao emitiu (FIS-40), via
 * NFeDistribuicaoDFe - webservice nacional, mas com um envelope SOAP
 * estruturalmente diferente dos demais servicos da NFe 4.00 usados neste
 * projeto: o Header vai vazio (sem nfeCabecMsg) e o Body envolve
 * nfeDadosMsg num elemento com o nome da propria operacao
 * (nfeDistDFeInteresse), em vez do padrao "bare" de SoapClient. Por isso
 * este cliente monta o proprio envelope em vez de reusar SoapClient.
 *
 * Fonte da estrutura do envelope e do formato de resposta: implementacao de
 * referencia do nfephp-org/sped-nfe (biblioteca PHP mais usada pela
 * comunidade de integradores NFe brasileira) - nao ha acesso ao WSDL oficial
 * nem a um ambiente de homologacao real nesta sessao para validar
 * empiricamente. Revisar contra homologacao real antes do primeiro uso em
 * producao.
 *
 * Consulta incremental por NSU: a SEFAZ devolve um lote de "docZip" (cada um
 * gzip+base64 de um resumo ou evento) a partir do ultNSU informado, junto do
 * novo ultNSU/maxNSU para a proxima chamada continuar de onde parou - nunca
 * do zero. Quem guarda esse cursor e o DistribuicaoDfeService (FIS-40), nao
 * este cliente.
 */
@Component
public class NfeDistribuicaoDfeClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe";
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_ULT_NSU = Pattern.compile("<ultNSU>(\\d+)</ultNSU>");
    private static final Pattern TAG_MAX_NSU = Pattern.compile("<maxNSU>(\\d+)</maxNSU>");
    private static final Pattern TAG_DOC_ZIP = Pattern.compile("<docZip NSU=\"(\\d+)\" schema=\"([^\"]+)\">([^<]+)</docZip>");
    private static final Pattern TAG_CH_NFE = Pattern.compile("<chNFe>(\\d+)</chNFe>");
    private static final Pattern TAG_CNPJ = Pattern.compile("<CNPJ>(\\d+)</CNPJ>");
    private static final Pattern TAG_X_NOME = Pattern.compile("<xNome>(.*?)</xNome>");
    private static final Pattern TAG_DH_EMI = Pattern.compile("<dhEmi>(.*?)</dhEmi>");
    private static final Pattern TAG_DH_RECBTO = Pattern.compile("<dhRecbto>(.*?)</dhRecbto>");
    private static final Pattern TAG_V_NF = Pattern.compile("<vNF>([\\d.]+)</vNF>");
    private static final Pattern TAG_C_SIT_NFE = Pattern.compile("<cSitNFe>(\\d+)</cSitNFe>");

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public NfeDistribuicaoDfeClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public RetornoDistribuicaoDfe consultarPorNsu(String cnpj, String ufAutor, String ultNsu, TipoAmbiente ambiente,
                                                   CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl("AN", ambiente, TipoServicoSefaz.DISTRIBUICAO_DFE);
        return consultarPorNsu(url, cnpj, ufAutor, ultNsu, ambiente, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    RetornoDistribuicaoDfe consultarPorNsu(String url, String cnpj, String ufAutor, String ultNsu,
                                            TipoAmbiente ambiente, HttpClient httpClient) {
        String cUfAutor = CodigoUfSefaz.codigo(ufAutor);
        String ultNsuFormatado = String.format("%015d", Long.parseLong(ultNsu));

        String distDFeInt = "<distDFeInt xmlns=\"http://www.portalfiscal.inf.br/nfe\" versao=\"1.01\">"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<cUFAutor>" + cUfAutor + "</cUFAutor>"
                + "<CNPJ>" + cnpj + "</CNPJ>"
                + "<distNSU><ultNSU>" + ultNsuFormatado + "</ultNSU></distNSU>"
                + "</distDFeInt>";

        String envelope = "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<nfeDistDFeInteresse xmlns=\"" + NAMESPACE + "\">"
                + "<nfeDadosMsg>" + distDFeInt + "</nfeDadosMsg>"
                + "</nfeDistDFeInteresse>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";

        return interpretar(enviar(httpClient, url, envelope));
    }

    private String enviar(HttpClient httpClient, String url, String envelope) {
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

    private RetornoDistribuicaoDfe interpretar(String respostaXml) {
        Matcher matcherStat = TAG_CSTAT.matcher(respostaXml);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de distribuicao DFe sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);

        Matcher matcherMotivo = TAG_XMOTIVO.matcher(respostaXml);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";

        Matcher matcherUltNsu = TAG_ULT_NSU.matcher(respostaXml);
        String ultNsu = matcherUltNsu.find() ? matcherUltNsu.group(1) : null;

        Matcher matcherMaxNsu = TAG_MAX_NSU.matcher(respostaXml);
        String maxNsu = matcherMaxNsu.find() ? matcherMaxNsu.group(1) : null;

        List<ResumoNfeDistribuicao> resumos = new ArrayList<>();
        Matcher matcherDocZip = TAG_DOC_ZIP.matcher(respostaXml);
        while (matcherDocZip.find()) {
            String schema = matcherDocZip.group(2);
            if (!schema.startsWith("resNFe")) {
                // resEvento/procNFe/procEventoNFe (eventos ja registrados, documento completo) ficam
                // fora do escopo desta consulta - o objetivo aqui e descobrir NF-e destinadas ainda nao
                // avaliadas (criterio de aceite "consulta de NF-e destinadas a empresa"), nao baixar o
                // acervo completo de documentos/eventos.
                continue;
            }
            resumos.add(interpretarResNfe(descompactar(matcherDocZip.group(3))));
        }

        return new RetornoDistribuicaoDfe(cStat, xMotivo, ultNsu, maxNsu, resumos);
    }

    private ResumoNfeDistribuicao interpretarResNfe(String resNfeXml) {
        String chaveAcesso = obrigatorio(TAG_CH_NFE, resNfeXml, "chNFe");
        String cnpjEmitente = obrigatorio(TAG_CNPJ, resNfeXml, "CNPJ");
        String nomeEmitente = valorOuNulo(TAG_X_NOME, resNfeXml);
        String dhEmi = valorOuNulo(TAG_DH_EMI, resNfeXml);
        String dhRecbto = valorOuNulo(TAG_DH_RECBTO, resNfeXml);
        String vNf = valorOuNulo(TAG_V_NF, resNfeXml);
        String cSitNFe = valorOuNulo(TAG_C_SIT_NFE, resNfeXml);

        return new ResumoNfeDistribuicao(chaveAcesso, cnpjEmitente, nomeEmitente,
                dhEmi != null ? OffsetDateTime.parse(dhEmi) : null,
                dhRecbto != null ? OffsetDateTime.parse(dhRecbto) : null,
                vNf != null ? new BigDecimal(vNf) : null,
                SituacaoNfeDistribuicao.de(cSitNFe));
    }

    private String obrigatorio(Pattern padrao, String xml, String nomeTag) {
        Matcher matcher = padrao.matcher(xml);
        if (!matcher.find()) {
            throw new SefazComunicacaoException("resNFe sem " + nomeTag + ": " + xml);
        }
        return matcher.group(1);
    }

    private String valorOuNulo(Pattern padrao, String xml) {
        Matcher matcher = padrao.matcher(xml);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String descompactar(String base64Gzip) {
        try {
            byte[] comprimido = Base64.getDecoder().decode(base64Gzip);
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(comprimido));
                 ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
                gzip.transferTo(saida);
                return saida.toString(StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new SefazComunicacaoException("Falha ao descompactar docZip da distribuicao DFe", e);
        }
    }
}
