package com.fiscaladapter.sefaz.cte;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Consulta a situacao de um CT-e ja transmitido, pela chave de acesso (CTeConsultaV4, FIS-44). */
@Component
public class CteConsultaProtocoloClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/cte/wsdl/CTeConsultaV4";
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_DHRECBTO = Pattern.compile("<dhRecbto>(.*?)</dhRecbto>");

    private final CteEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public CteConsultaProtocoloClient(CteEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public ConsultaProtocoloResponse consultar(String chaveAcesso, String uf, TipoAmbiente ambiente,
                                                CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoCte.CONSULTA_PROTOCOLO);
        return consultar(url, chaveAcesso, ambiente, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    ConsultaProtocoloResponse consultar(String url, String chaveAcesso, TipoAmbiente ambiente, HttpClient httpClient) {
        String cUF = chaveAcesso.substring(0, 2);

        String consSitCTe = "<consSitCTe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/cte\">"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<xServ>CONSULTAR</xServ>"
                + "<chCTe>" + chaveAcesso + "</chCTe>"
                + "</consSitCTe>";

        String respostaXml = CteSoapClient.enviarTextoPuro(httpClient, url, NAMESPACE, cUF, "4.00", consSitCTe);

        return interpretar(respostaXml);
    }

    private ConsultaProtocoloResponse interpretar(String respostaXml) {
        Matcher matcherStat = TAG_CSTAT.matcher(respostaXml);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(respostaXml);
        Matcher matcherProtocolo = TAG_NPROT.matcher(respostaXml);
        Matcher matcherDhRecbto = TAG_DHRECBTO.matcher(respostaXml);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de consulta do CT-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        String nProt = matcherProtocolo.find() ? matcherProtocolo.group(1) : null;
        String dhRecbto = matcherDhRecbto.find() ? matcherDhRecbto.group(1) : null;
        return ConsultaProtocoloResponse.de(cStat, xMotivo, nProt, dhRecbto);
    }
}
