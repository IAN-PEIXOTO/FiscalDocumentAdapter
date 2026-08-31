package com.fiscaladapter.sefaz.cte;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transmite o CT-e assinado para autorizacao (FIS-44), servico
 * CTeRecepcaoSincV4 - unico modo em uso desde que a SEFAZ desativou o modo
 * em lote (CTeRecepcao/CTeRetRecepcao) em 30/06/2024 (NT 2024.001). Um
 * documento por chamada, resposta imediata (sem idLote/indSinc como na
 * NFe - o CT-e 4.00 nunca teve um equivalente sincrono/assincrono
 * selecionavel, "sincrono sem lote" e o unico formato do servico).
 *
 * O corpo da requisicao vai gzip+base64 (verificado contra a implementacao
 * de referencia nfephp-org/sped-cte, Common/Tools.php::sefazEnviaCTe) -
 * diferente da NFe, cujo enviNFe vai em texto puro.
 */
@Component
public class CteAutorizacaoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoSincV4";
    private static final Pattern TAG_PROT_CTE = Pattern.compile("<protCTe.*?</protCTe>", Pattern.DOTALL);
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_DHRECBTO = Pattern.compile("<dhRecbto>(.*?)</dhRecbto>");

    private final CteEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public CteAutorizacaoClient(CteEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public AutorizacaoResponse autorizar(String cteXmlAssinado, String uf, TipoAmbiente ambiente,
                                          CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoCte.AUTORIZACAO);
        return autorizar(url, cteXmlAssinado, uf, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    AutorizacaoResponse autorizar(String url, String cteXmlAssinado, String uf, HttpClient httpClient) {
        String cUF = CodigoUfSefaz.codigo(uf);
        String xmlSemDeclaracao = cteXmlAssinado.replaceFirst("<\\?xml[^>]*\\?>", "");

        String respostaXml = CteSoapClient.enviarComprimido(httpClient, url, NAMESPACE, cUF, "4.00", xmlSemDeclaracao);

        return interpretar(respostaXml);
    }

    private AutorizacaoResponse interpretar(String respostaXml) {
        Matcher matcherProtCte = TAG_PROT_CTE.matcher(respostaXml);
        if (matcherProtCte.find()) {
            String bloco = matcherProtCte.group();
            Matcher matcherStat = TAG_CSTAT.matcher(bloco);
            Matcher matcherMotivo = TAG_XMOTIVO.matcher(bloco);
            Matcher matcherProt = TAG_NPROT.matcher(bloco);
            Matcher matcherDhRecbto = TAG_DHRECBTO.matcher(bloco);
            String cStat = matcherStat.find() ? matcherStat.group(1) : "";
            String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
            String nProt = matcherProt.find() ? matcherProt.group(1) : null;
            String dhRecbto = matcherDhRecbto.find() ? matcherDhRecbto.group(1) : null;
            return AutorizacaoResponse.de(cStat, xMotivo, nProt, dhRecbto);
        }

        // sem protCTe: rejeitado antes de processar (ex.: erro de schema)
        Matcher matcherStatRaiz = TAG_CSTAT.matcher(respostaXml);
        Matcher matcherMotivoRaiz = TAG_XMOTIVO.matcher(respostaXml);
        if (!matcherStatRaiz.find()) {
            throw new SefazComunicacaoException("Resposta de autorizacao do CT-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStatRaiz.group(1);
        String xMotivo = matcherMotivoRaiz.find() ? matcherMotivoRaiz.group(1) : "";
        return AutorizacaoResponse.de(cStat, xMotivo, null, null);
    }
}
