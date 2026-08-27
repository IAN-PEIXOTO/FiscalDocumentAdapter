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

/** Consulta se o webservice da SEFAZ de uma UF esta disponivel (nfeStatusServico4). */
@Component
public class NfeStatusServicoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4";
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public NfeStatusServicoClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public StatusServicoResponse consultar(String uf, TipoAmbiente ambiente, CertificadoCarregado certificado) {
        return consultar(uf, ambiente, httpClientFactory.criar(certificado));
    }

    StatusServicoResponse consultar(String uf, TipoAmbiente ambiente, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoSefaz.STATUS_SERVICO);
        return consultar(url, uf, ambiente, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    StatusServicoResponse consultar(String url, String uf, TipoAmbiente ambiente, HttpClient httpClient) {
        String cUF = CodigoUfSefaz.codigo(uf);
        int tpAmb = ambiente.codigo();

        String consStatServ = "<consStatServ versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<tpAmb>" + tpAmb + "</tpAmb>"
                + "<cUF>" + cUF + "</cUF>"
                + "<xServ>STATUS</xServ>"
                + "</consStatServ>";

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, cUF, "4.00", consStatServ);

        return interpretar(respostaXml);
    }

    private StatusServicoResponse interpretar(String respostaXml) {
        Matcher matcherStat = TAG_CSTAT.matcher(respostaXml);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(respostaXml);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de status de servico sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        return StatusServicoResponse.de(cStat, xMotivo);
    }
}
