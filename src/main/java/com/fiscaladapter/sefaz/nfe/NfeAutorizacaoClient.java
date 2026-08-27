package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transmite a NFe assinada para autorizacao (nfeAutorizacao4), em modo
 * sincrono (indSinc=1) - suportado por todas as UFs para lotes de um unico
 * documento, evitando ter que implementar o polling de nfeRetAutorizacao4
 * separadamente nesta primeira versao.
 */
@Component
public class NfeAutorizacaoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4";
    private static final Pattern TAG_CSTAT_RAIZ = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO_RAIZ = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_PROT_NFE = Pattern.compile("<protNFe.*?</protNFe>", Pattern.DOTALL);
    private static final Pattern TAG_CSTAT_PROT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO_PROT = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_DHRECBTO = Pattern.compile("<dhRecbto>(.*?)</dhRecbto>");

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public NfeAutorizacaoClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public AutorizacaoResponse autorizar(String xmlNfeAssinado, String uf, TipoAmbiente ambiente,
                                          CertificadoCarregado certificado) {
        return autorizar(xmlNfeAssinado, uf, uf, ambiente, httpClientFactory.criar(certificado));
    }

    /**
     * Em contingencia, o envio vai para o endpoint da SVC (chaveEndpoint =
     * "SVC-AN"/"SVC-RS"), mas o cUF do envelope continua sendo o da UF do
     * emitente - por isso os dois parametros separados (ver FIS-37).
     */
    public AutorizacaoResponse autorizar(String xmlNfeAssinado, String ufEmitente, String chaveEndpoint,
                                          TipoAmbiente ambiente, CertificadoCarregado certificado) {
        return autorizar(xmlNfeAssinado, ufEmitente, chaveEndpoint, ambiente, httpClientFactory.criar(certificado));
    }

    AutorizacaoResponse autorizar(String xmlNfeAssinado, String ufEmitente, TipoAmbiente ambiente, HttpClient httpClient) {
        return autorizar(xmlNfeAssinado, ufEmitente, ufEmitente, ambiente, httpClient);
    }

    AutorizacaoResponse autorizar(String xmlNfeAssinado, String ufEmitente, String chaveEndpoint,
                                   TipoAmbiente ambiente, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(chaveEndpoint, ambiente, TipoServicoSefaz.AUTORIZACAO);
        return autorizarNoEndpoint(url, xmlNfeAssinado, ufEmitente, ambiente, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    AutorizacaoResponse autorizarNoEndpoint(String url, String xmlNfeAssinado, String ufEmitente, TipoAmbiente ambiente, HttpClient httpClient) {
        String cUF = CodigoUfSefaz.codigo(ufEmitente);
        String idLote = String.valueOf(System.nanoTime() % 1_000_000_000L);

        String enviNFe = "<enviNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<idLote>" + idLote + "</idLote>"
                + "<indSinc>1</indSinc>"
                + xmlNfeAssinado
                + "</enviNFe>";

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, cUF, "4.00", enviNFe);

        return interpretar(respostaXml);
    }

    private AutorizacaoResponse interpretar(String respostaXml) {
        Matcher matcherProtNFe = TAG_PROT_NFE.matcher(respostaXml);
        if (matcherProtNFe.find()) {
            String blocoProtocolo = matcherProtNFe.group();
            Matcher matcherStat = TAG_CSTAT_PROT.matcher(blocoProtocolo);
            Matcher matcherMotivo = TAG_XMOTIVO_PROT.matcher(blocoProtocolo);
            Matcher matcherProt = TAG_NPROT.matcher(blocoProtocolo);
            Matcher matcherDhRecbto = TAG_DHRECBTO.matcher(blocoProtocolo);
            String cStat = matcherStat.find() ? matcherStat.group(1) : "";
            String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
            String nProt = matcherProt.find() ? matcherProt.group(1) : null;
            String dhRecbto = matcherDhRecbto.find() ? matcherDhRecbto.group(1) : null;
            return AutorizacaoResponse.de(cStat, xMotivo, nProt, dhRecbto);
        }

        // sem protNFe: lote rejeitado antes de processar o documento (ex.: erro de schema/duplicidade de lote)
        Matcher matcherStatRaiz = TAG_CSTAT_RAIZ.matcher(respostaXml);
        Matcher matcherMotivoRaiz = TAG_XMOTIVO_RAIZ.matcher(respostaXml);
        if (!matcherStatRaiz.find()) {
            throw new SefazComunicacaoException("Resposta de autorizacao sem cStat: " + respostaXml);
        }
        String cStat = matcherStatRaiz.group(1);
        String xMotivo = matcherMotivoRaiz.find() ? matcherMotivoRaiz.group(1) : "";
        return AutorizacaoResponse.de(cStat, xMotivo, null, null);
    }
}
