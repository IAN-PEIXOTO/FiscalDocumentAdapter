package com.fiscaladapter.sefaz.cte;

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
 * Cancela um CT-e autorizado, via evento tpEvento=110111 (FIS-44), servico
 * CTeRecepcaoEventoV4. Estrutura verificada contra a implementacao de
 * referencia (nfephp-org/sped-cte, Common/Tools.php::sefazCancela/sefazEvento):
 * elemento raiz "eventoCTe" (nao "evento" como na NFe), enviado direto em
 * cteDadosMsg sem wrapper de lote (nao ha idLote/envEvento para o CT-e) e
 * texto puro (sem gzip, diferente da autorizacao). cOrgao e o codigo da UF
 * do proprio emitente, nao um codigo fixo de Ambiente Nacional (o
 * cancelamento do CT-e vai para o mesmo endpoint por UF/SVRS da
 * autorizacao/consulta, sem infraestrutura nacional separada).
 *
 * ATENCAO (nao verificavel nesta sessao): a versao do evento (atributo
 * "versao"/"versaoEvento") foi assumida como "4.00" - alinhada a URL do
 * servico (CTeRecepcaoEventoV4) - mas nao confirmada contra homologacao
 * real, ja que a NFe usa uma versao de evento fixa ("1.00") independente do
 * layout do documento, e nao ha garantia de que o CT-e siga o mesmo padrao
 * ou trate o evento com versionamento proprio.
 */
@Component
public class CteCancelamentoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/cte/wsdl/CTeRecepcaoEventoV4";
    private static final String TP_EVENTO_CANCELAMENTO = "110111";
    private static final String VERSAO_EVENTO = "4.00";
    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEventoCTe.*?</retEventoCTe>", Pattern.DOTALL);

    private final CteEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;

    public CteCancelamentoClient(CteEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                                  AssinaturaXmlService assinaturaXmlService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
    }

    public CancelamentoResponse cancelar(String chaveAcesso, String numeroProtocolo, String justificativa,
                                          String uf, TipoAmbiente ambiente, CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoCte.RECEPCAO_EVENTO);
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

        String eventoSemAssinatura = "<eventoCTe versao=\"" + VERSAO_EVENTO + "\" xmlns=\"http://www.portalfiscal.inf.br/cte\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + cUF + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + extrairCnpjDoCertificado(certificado) + "</CNPJ>"
                + "<chCTe>" + chaveAcesso + "</chCTe>"
                + "<dhEvento>" + OffsetDateTime.now().format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_CANCELAMENTO + "</tpEvento>"
                + "<nSeqEvento>" + Integer.parseInt(nSeqEvento) + "</nSeqEvento>"
                + "<detEvento versaoEvento=\"" + VERSAO_EVENTO + "\">"
                + "<evCancCTe>"
                + "<descEvento>Cancelamento</descEvento>"
                + "<nProt>" + escaparXml(numeroProtocolo) + "</nProt>"
                + "<xJust>" + escaparXml(justificativa) + "</xJust>"
                + "</evCancCTe>"
                + "</detEvento>"
                + "</infEvento>"
                + "</eventoCTe>";

        String eventoAssinado = assinaturaXmlService.assinar(eventoSemAssinatura, id, certificado);
        String eventoSemDeclaracao = eventoAssinado.replaceFirst("<\\?xml[^>]*\\?>", "");

        String respostaXml = CteSoapClient.enviarTextoPuro(httpClient, url, NAMESPACE, cUF, VERSAO_EVENTO, eventoSemDeclaracao);

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
            throw new SefazComunicacaoException("Resposta de cancelamento do CT-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        return CancelamentoResponse.de(cStat, xMotivo);
    }
}
