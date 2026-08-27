package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cancela uma NFe autorizada, via evento tpEvento=110111 (recepcaoEvento4).
 * O CNPJ do autor do evento e extraido do proprio certificado usado para
 * assinar, seguindo o mesmo padrao ICP-Brasil usado em CertificadoDigitalService.
 */
@Component
public class NfeCancelamentoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    private static final String TP_EVENTO_CANCELAMENTO = "110111";
    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEvento.*?</retEvento>", Pattern.DOTALL);

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;

    public NfeCancelamentoClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                                  AssinaturaXmlService assinaturaXmlService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
    }

    public CancelamentoResponse cancelar(String chaveAcesso, String numeroProtocolo, String justificativa,
                                          String uf, TipoAmbiente ambiente, CertificadoCarregado certificado) {
        return cancelar(chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificado,
                httpClientFactory.criar(certificado));
    }

    CancelamentoResponse cancelar(String chaveAcesso, String numeroProtocolo, String justificativa, String uf,
                                   TipoAmbiente ambiente, CertificadoCarregado certificado, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoSefaz.RECEPCAO_EVENTO);
        return cancelar(url, chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificado, httpClient);
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

        String eventoSemAssinatura = "<evento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + cUF + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + extrairCnpjDoCertificado(certificado) + "</CNPJ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + OffsetDateTime.now().format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_CANCELAMENTO + "</tpEvento>"
                + "<nSeqEvento>" + Integer.parseInt(nSeqEvento) + "</nSeqEvento>"
                + "<verEvento>1.00</verEvento>"
                + "<detEvento versao=\"1.00\">"
                + "<descEvento>Cancelamento</descEvento>"
                + "<nProt>" + numeroProtocolo + "</nProt>"
                + "<xJust>" + escaparXml(justificativa) + "</xJust>"
                + "</detEvento>"
                + "</infEvento>"
                + "</evento>";

        String eventoAssinado = assinaturaXmlService.assinar(eventoSemAssinatura, id, certificado);

        String idLote = String.valueOf(System.nanoTime() % 1_000_000_000L);
        String envEvento = "<envEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<idLote>" + idLote + "</idLote>"
                + eventoAssinado.replaceFirst("<\\?xml[^>]*\\?>", "")
                + "</envEvento>";

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, cUF, "1.00", envEvento);

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
        // o cStat do evento em si (dentro de retEvento/infEvento) e o que importa, nao o
        // cStat do lote (retEnvEvento), que so indica se o lote foi recebido/processado
        Matcher matcherRetEvento = TAG_RET_EVENTO.matcher(respostaXml);
        String trecho = matcherRetEvento.find() ? matcherRetEvento.group() : respostaXml;

        Matcher matcherStat = TAG_CSTAT.matcher(trecho);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(trecho);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de cancelamento sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        return CancelamentoResponse.de(cStat, xMotivo);
    }
}
