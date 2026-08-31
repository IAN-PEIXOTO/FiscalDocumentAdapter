package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cancela um MDF-e autorizado, via evento tpEvento=110111 (FIS-45), servico
 * MDFeRecepcaoEvento. Mesma estrutura do cancelamento de CT-e (FIS-44):
 * elemento raiz "eventoMDFe", enviado direto em mdfeDadosMsg sem wrapper de
 * lote, texto puro (sem gzip). cOrgao e o codigo da UF do proprio emitente
 * (nao ha infraestrutura de Ambiente Nacional separada para eventos do
 * MDF-e). nSeqEvento com 2 digitos (verificado contra
 * MdfeEncerramentoXmlGenerator/MdfeEncerramentoXmlGeneratorTest, ja
 * existentes desde o FIS-19 - diferente do padrao de 3 digitos usado por
 * NFe/CT-e).
 */
@Component
public class MdfeCancelamentoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento";
    private static final String TP_EVENTO_CANCELAMENTO = "110111";
    private static final String VERSAO_EVENTO = "3.00";
    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEventoMDFe.*?</retEventoMDFe>", Pattern.DOTALL);

    private final MdfeEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;

    public MdfeCancelamentoClient(MdfeEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                                   AssinaturaXmlService assinaturaXmlService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
    }

    public CancelamentoResponse cancelar(String chaveAcesso, String numeroProtocolo, String justificativa,
                                          String uf, TipoAmbiente ambiente, CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoMdfe.RECEPCAO_EVENTO);
        return cancelar(url, chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificado,
                httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    CancelamentoResponse cancelar(String url, String chaveAcesso, String numeroProtocolo, String justificativa, String uf,
                                   TipoAmbiente ambiente, CertificadoCarregado certificado, HttpClient httpClient) {
        if (justificativa.length() < 15) {
            throw new IllegalArgumentException("Justificativa do cancelamento deve ter pelo menos 15 caracteres");
        }

        String cUF = CodigoUfSefaz.codigo(uf);
        String nSeqEvento = "01";
        String id = "ID" + TP_EVENTO_CANCELAMENTO + chaveAcesso + nSeqEvento;

        String eventoSemAssinatura = "<eventoMDFe versao=\"" + VERSAO_EVENTO + "\" xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + cUF + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + extrairCnpjDoCertificado(certificado) + "</CNPJ>"
                + "<chMDFe>" + chaveAcesso + "</chMDFe>"
                + "<dhEvento>" + OffsetDateTime.now().format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_CANCELAMENTO + "</tpEvento>"
                + "<nSeqEvento>" + nSeqEvento + "</nSeqEvento>"
                + "<detEvento versaoEvento=\"" + VERSAO_EVENTO + "\">"
                + "<evCancMDFe>"
                + "<descEvento>Cancelamento</descEvento>"
                + "<nProt>" + numeroProtocolo + "</nProt>"
                + "<xJust>" + escaparXml(justificativa) + "</xJust>"
                + "</evCancMDFe>"
                + "</detEvento>"
                + "</infEvento>"
                + "</eventoMDFe>";

        String eventoAssinado = assinaturaXmlService.assinar(eventoSemAssinatura, id, certificado);
        String eventoSemDeclaracao = eventoAssinado.replaceFirst("<\\?xml[^>]*\\?>", "");

        String respostaXml = MdfeSoapClient.enviarTextoPuro(httpClient, url, NAMESPACE, cUF, VERSAO_EVENTO, eventoSemDeclaracao);

        return interpretar(respostaXml);
    }

    private String extrairCnpjDoCertificado(CertificadoCarregado certificado) {
        if (certificado.info().cnpj() != null) {
            return certificado.info().cnpj();
        }
        throw new SefazComunicacaoException("Nao foi possivel determinar o CNPJ do autor do evento a partir do certificado");
    }

    private String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private CancelamentoResponse interpretar(String respostaXml) {
        Matcher matcherRetEvento = TAG_RET_EVENTO.matcher(respostaXml);
        String trecho = matcherRetEvento.find() ? matcherRetEvento.group() : respostaXml;

        Matcher matcherStat = TAG_CSTAT.matcher(trecho);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(trecho);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de cancelamento do MDF-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        return CancelamentoResponse.de(cStat, xMotivo);
    }
}
