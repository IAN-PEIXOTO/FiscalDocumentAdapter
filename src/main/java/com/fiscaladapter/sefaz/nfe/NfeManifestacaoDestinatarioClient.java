package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
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
 * Registra a Manifestacao do Destinatario (FIS-9/FIS-40): confirmacao,
 * ciencia, desconhecimento ou nao realizacao da operacao descrita numa NFe
 * recebida. Diferente de cancelamento/CC-e (que o EMITENTE faz na propria
 * UF), a manifestacao e feita pelo DESTINATARIO usando o certificado do seu
 * proprio CNPJ (ja suportado pelo mesmo cadastro do FIS-2 - o mesmo registro
 * de certificado serve tanto para emitir quanto para manifestar, dependendo
 * de quem chama a API), e e sempre recebida pelo Ambiente Nacional - AN
 * (cOrgao=91, mesmo endpoint do EPEC - ver NfeEpecClient), independente da
 * UF do destinatario ou do emitente da nota. Estrutura de cada evento
 * conferida contra os XSDs oficiais (e210200/e210210/e210220/e210240
 * v1.00.xsd).
 *
 * Fora do escopo: certificado e-CPF (destinatario pessoa fisica) - o cadastro
 * de certificados (FIS-2) hoje so suporta CNPJ.
 */
@Component
public class NfeManifestacaoDestinatarioClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    private static final String COD_ORGAO_AN = "91";
    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEvento.*?</retEvento>", Pattern.DOTALL);

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;

    public NfeManifestacaoDestinatarioClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                                              AssinaturaXmlService assinaturaXmlService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
    }

    public ManifestacaoResponse manifestar(String chaveAcesso, TipoManifestacaoDestinatario tipo, String justificativa,
                                            TipoAmbiente ambiente, CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl("AN", ambiente, TipoServicoSefaz.RECEPCAO_EVENTO);
        return manifestar(url, chaveAcesso, tipo, justificativa, ambiente, certificado, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    ManifestacaoResponse manifestar(String url, String chaveAcesso, TipoManifestacaoDestinatario tipo, String justificativa,
                                     TipoAmbiente ambiente, CertificadoCarregado certificado, HttpClient httpClient) {
        if (tipo.exigeJustificativa() && (justificativa == null || justificativa.length() < 15)) {
            throw new IllegalArgumentException(
                    "Justificativa do evento " + tipo.descEvento() + " deve ter pelo menos 15 caracteres");
        }

        String nSeqEvento = "01";
        String id = "ID" + tipo.tpEvento() + chaveAcesso + nSeqEvento;

        String tagJust = tipo.exigeJustificativa() ? "<xJust>" + escaparXml(justificativa) + "</xJust>" : "";

        String eventoSemAssinatura = "<evento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + COD_ORGAO_AN + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + extrairCnpjDoCertificado(certificado) + "</CNPJ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + OffsetDateTime.now().format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + tipo.tpEvento() + "</tpEvento>"
                + "<nSeqEvento>" + Integer.parseInt(nSeqEvento) + "</nSeqEvento>"
                + "<verEvento>1.00</verEvento>"
                + "<detEvento versao=\"1.00\">"
                + "<descEvento>" + tipo.descEvento() + "</descEvento>"
                + tagJust
                + "</detEvento>"
                + "</infEvento>"
                + "</evento>";

        String eventoAssinado = assinaturaXmlService.assinar(eventoSemAssinatura, id, certificado);

        String idLote = String.valueOf(System.nanoTime() % 1_000_000_000L);
        String envEvento = "<envEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<idLote>" + idLote + "</idLote>"
                + eventoAssinado.replaceFirst("<\\?xml[^>]*\\?>", "")
                + "</envEvento>";

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, COD_ORGAO_AN, "1.00", envEvento);

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

    private ManifestacaoResponse interpretar(String respostaXml) {
        Matcher matcherRetEvento = TAG_RET_EVENTO.matcher(respostaXml);
        String trecho = matcherRetEvento.find() ? matcherRetEvento.group() : respostaXml;

        Matcher matcherStat = TAG_CSTAT.matcher(trecho);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(trecho);
        Matcher matcherProt = TAG_NPROT.matcher(trecho);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de manifestacao sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        String nProt = matcherProt.find() ? matcherProt.group(1) : null;
        return ManifestacaoResponse.de(cStat, xMotivo, nProt);
    }
}
