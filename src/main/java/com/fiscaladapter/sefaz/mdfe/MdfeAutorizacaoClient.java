package com.fiscaladapter.sefaz.mdfe;

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
 * Transmite o MDF-e assinado para autorizacao (FIS-45), servico
 * MDFeRecepcaoSinc - unico modo em uso desde que a SEFAZ desativou o modo
 * em lote (MDFeRecepcao/MDFeRetRecepcao) em 30/06/2024 (NT 2024.001), mesma
 * migracao do CT-e (FIS-44). Corpo gzip+base64, verificado contra a
 * implementacao de referencia nfephp-org/sped-mdfe (Tools.php::sefazEnviaLote,
 * ramo indSinc=1).
 */
@Component
public class MdfeAutorizacaoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoSinc";
    private static final Pattern TAG_PROT_MDFE = Pattern.compile("<protMDFe.*?</protMDFe>", Pattern.DOTALL);
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_DHRECBTO = Pattern.compile("<dhRecbto>(.*?)</dhRecbto>");

    private final MdfeEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public MdfeAutorizacaoClient(MdfeEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public AutorizacaoResponse autorizar(String mdfeXmlAssinado, String uf, TipoAmbiente ambiente,
                                          CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoMdfe.AUTORIZACAO);
        return autorizar(url, mdfeXmlAssinado, uf, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    AutorizacaoResponse autorizar(String url, String mdfeXmlAssinado, String uf, HttpClient httpClient) {
        String cUF = CodigoUfSefaz.codigo(uf);
        String xmlSemDeclaracao = mdfeXmlAssinado.replaceFirst("<\\?xml[^>]*\\?>", "");

        String respostaXml = MdfeSoapClient.enviarComprimido(httpClient, url, NAMESPACE, cUF, "3.00", xmlSemDeclaracao);

        return interpretar(respostaXml);
    }

    private AutorizacaoResponse interpretar(String respostaXml) {
        Matcher matcherProtMdfe = TAG_PROT_MDFE.matcher(respostaXml);
        if (matcherProtMdfe.find()) {
            String bloco = matcherProtMdfe.group();
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

        // sem protMDFe: rejeitado antes de processar (ex.: erro de schema)
        Matcher matcherStatRaiz = TAG_CSTAT.matcher(respostaXml);
        Matcher matcherMotivoRaiz = TAG_XMOTIVO.matcher(respostaXml);
        if (!matcherStatRaiz.find()) {
            throw new SefazComunicacaoException("Resposta de autorizacao do MDF-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStatRaiz.group(1);
        String xMotivo = matcherMotivoRaiz.find() ? matcherMotivoRaiz.group(1) : "";
        return AutorizacaoResponse.de(cStat, xMotivo, null, null);
    }
}
