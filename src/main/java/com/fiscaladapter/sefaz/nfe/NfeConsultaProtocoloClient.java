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

/** Consulta a situacao de uma NFe ja transmitida, pela chave de acesso (nfeConsultaProtocolo4). */
@Component
public class NfeConsultaProtocoloClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4";
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_DHRECBTO = Pattern.compile("<dhRecbto>(.*?)</dhRecbto>");

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public NfeConsultaProtocoloClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public ConsultaProtocoloResponse consultar(String chaveAcesso, String uf, TipoAmbiente ambiente,
                                                CertificadoCarregado certificado) {
        return consultar(chaveAcesso, uf, ambiente, httpClientFactory.criar(certificado));
    }

    ConsultaProtocoloResponse consultar(String chaveAcesso, String uf, TipoAmbiente ambiente, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoSefaz.CONSULTA_PROTOCOLO);
        return consultar(url, chaveAcesso, uf, ambiente, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    ConsultaProtocoloResponse consultar(String url, String chaveAcesso, String uf, TipoAmbiente ambiente, HttpClient httpClient) {
        String cUF = CodigoUfSefaz.codigo(uf);

        String consSitNFe = "<consSitNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<xServ>CONSULTAR</xServ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "</consSitNFe>";

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, cUF, "4.00", consSitNFe);

        return interpretar(respostaXml);
    }

    private ConsultaProtocoloResponse interpretar(String respostaXml) {
        Matcher matcherStat = TAG_CSTAT.matcher(respostaXml);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(respostaXml);
        Matcher matcherProtocolo = TAG_NPROT.matcher(respostaXml);
        Matcher matcherDhRecbto = TAG_DHRECBTO.matcher(respostaXml);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de consulta sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        String nProt = matcherProtocolo.find() ? matcherProtocolo.group(1) : null;
        String dhRecbto = matcherDhRecbto.find() ? matcherDhRecbto.group(1) : null;
        return ConsultaProtocoloResponse.de(cStat, xMotivo, nProt, dhRecbto);
    }
}
