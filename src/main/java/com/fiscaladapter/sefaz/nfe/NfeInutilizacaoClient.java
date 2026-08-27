package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.Year;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inutiliza uma faixa de numeracao de NFe que nao vai ser usada (nfeInutilizacaoNF4) -
 * exigencia legal: toda numeracao pulada precisa ser formalmente inutilizada junto
 * a SEFAZ, dentro do prazo legal (ate o 10º dia do mes seguinte). Diferente de
 * cancelamento/CCe, isto NAO e um evento (nao usa RECEPCAO_EVENTO) - e um servico
 * proprio (inutNFe/retInutNFe), com estrutura estavel e documentada de forma
 * consistente nas implementacoes de referencia (ACBr, nfephp) desde o layout 4.00.
 */
@Component
public class NfeInutilizacaoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeInutilizacao4";
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;
    private final ChaveAcessoService chaveAcessoService;

    public NfeInutilizacaoClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                                  AssinaturaXmlService assinaturaXmlService, ChaveAcessoService chaveAcessoService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
        this.chaveAcessoService = chaveAcessoService;
    }

    public InutilizacaoResponse inutilizar(String cnpjEmitente, String uf, int serie, long numeroInicial,
                                            long numeroFinal, String justificativa, TipoAmbiente ambiente,
                                            CertificadoCarregado certificado) {
        return inutilizar(cnpjEmitente, uf, serie, numeroInicial, numeroFinal, justificativa, ambiente, certificado,
                httpClientFactory.criar(certificado));
    }

    InutilizacaoResponse inutilizar(String cnpjEmitente, String uf, int serie, long numeroInicial, long numeroFinal,
                                     String justificativa, TipoAmbiente ambiente, CertificadoCarregado certificado,
                                     HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoSefaz.INUTILIZACAO);
        return inutilizar(url, cnpjEmitente, uf, serie, numeroInicial, numeroFinal, justificativa, ambiente, certificado, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    InutilizacaoResponse inutilizar(String url, String cnpjEmitente, String uf, int serie, long numeroInicial,
                                     long numeroFinal, String justificativa, TipoAmbiente ambiente,
                                     CertificadoCarregado certificado, HttpClient httpClient) {
        if (justificativa.length() < 15) {
            throw new IllegalArgumentException("Justificativa da inutilizacao deve ter pelo menos 15 caracteres");
        }
        if (numeroFinal < numeroInicial) {
            throw new IllegalArgumentException("nNFFin nao pode ser menor que nNFIni");
        }

        String cUF = CodigoUfSefaz.codigo(uf);
        String ano = String.format("%02d", Year.now().getValue() % 100);
        String cnpj = cnpjEmitente.replaceAll("\\D", "");
        String mod = chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFE);
        String serieFormatada = String.format("%03d", serie);
        String nNFIni = String.format("%09d", numeroInicial);
        String nNFFin = String.format("%09d", numeroFinal);
        String id = "ID" + cUF + ano + cnpj + mod + serieFormatada + nNFIni + nNFFin;

        String inutNFeSemAssinatura = "<inutNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infInut Id=\"" + id + "\">"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<xServ>INUTILIZAR</xServ>"
                + "<cUF>" + cUF + "</cUF>"
                + "<ano>" + ano + "</ano>"
                + "<CNPJ>" + cnpj + "</CNPJ>"
                + "<mod>" + mod + "</mod>"
                + "<serie>" + serie + "</serie>"
                + "<nNFIni>" + numeroInicial + "</nNFIni>"
                + "<nNFFin>" + numeroFinal + "</nNFFin>"
                + "<xJust>" + escaparXml(justificativa) + "</xJust>"
                + "</infInut>"
                + "</inutNFe>";

        String inutNFeAssinado = assinaturaXmlService.assinar(inutNFeSemAssinatura, id, certificado);

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, cUF, "4.00", inutNFeAssinado);

        return interpretar(respostaXml);
    }

    private String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private InutilizacaoResponse interpretar(String respostaXml) {
        Matcher matcherStat = TAG_CSTAT.matcher(respostaXml);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(respostaXml);
        Matcher matcherProtocolo = TAG_NPROT.matcher(respostaXml);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de inutilizacao sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        String nProt = matcherProtocolo.find() ? matcherProtocolo.group(1) : null;
        return InutilizacaoResponse.de(cStat, xMotivo, nProt);
    }
}
