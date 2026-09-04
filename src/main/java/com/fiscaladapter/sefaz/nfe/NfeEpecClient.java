package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.nfe.Destinatario;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registra o evento previo de emissao em contingencia - EPEC (tpEvento=110140),
 * ultimo recurso quando nem o endpoint normal da UF nem a SVC de contingencia
 * respondem (FIS-7/FIS-37). Diferente do SVC-AN/SVC-RS (que autorizam a propria
 * NFe), o EPEC so registra um evento resumido que autoriza provisoriamente a
 * circulacao da mercadoria - a NFe completa (ja gerada com tpEmis=4) so sera
 * de fato autorizada quando puder ser transmitida normalmente, o que exige uma
 * fila de retomada assincrona (fora do escopo aqui - ver FIS-30).
 *
 * O evento EPEC e sempre recebido pelo Ambiente Nacional - AN (cOrgao=91 fixo,
 * independente da UF do emitente; NAO e a SVC-RS, que tem seu proprio codigo
 * 93 e nem sequer expõe RecepcaoEvento para uso geral) - estrutura conferida
 * contra o XSD oficial (eventoEPEC_v1.00), a base de enderecos e a
 * implementacao de referencia nfephp-org/sped-nfe (Tools::sefazEPEC,
 * storage/wsnfe_4.00_mod55.xml, sped-common/UFList), incluindo o codigo de
 * sucesso do evento (cStat 136, diferente do 135 usado por cancelamento/CC-e).
 */
@Component
public class NfeEpecClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    private static final String TP_EVENTO_EPEC = "110140";
    private static final String COD_ORGAO_EPEC = "91";
    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEvento.*?</retEvento>", Pattern.DOTALL);

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;

    public NfeEpecClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                          AssinaturaXmlService assinaturaXmlService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
    }

    public EpecResponse registrar(NotaFiscalEletronica nfe, String chaveAcesso, TipoAmbiente ambiente,
                                   CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl("AN", ambiente, TipoServicoSefaz.RECEPCAO_EVENTO);
        return registrar(url, nfe, chaveAcesso, ambiente, certificado, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    EpecResponse registrar(String url, NotaFiscalEletronica nfe, String chaveAcesso, TipoAmbiente ambiente,
                            CertificadoCarregado certificado, HttpClient httpClient) {
        String cOrgaoAutor = CodigoUfSefaz.codigo(nfe.identificacao().uf());
        String nSeqEvento = "01";
        String id = "ID" + TP_EVENTO_EPEC + chaveAcesso + nSeqEvento;

        String eventoSemAssinatura = "<evento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + COD_ORGAO_EPEC + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + extrairCnpjDoCertificado(certificado) + "</CNPJ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + OffsetDateTime.now().format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_EPEC + "</tpEvento>"
                + "<nSeqEvento>" + Integer.parseInt(nSeqEvento) + "</nSeqEvento>"
                + "<verEvento>1.00</verEvento>"
                + "<detEvento versao=\"1.00\">"
                + "<descEvento>EPEC</descEvento>"
                + "<cOrgaoAutor>" + cOrgaoAutor + "</cOrgaoAutor>"
                + "<tpAutor>1</tpAutor>"
                + "<verAplic>1.0.0</verAplic>"
                + "<dhEmi>" + nfe.identificacao().dataEmissao()
                        .atStartOfDay(java.time.ZoneId.systemDefault()).format(DATA_EVENTO_FORMAT) + "</dhEmi>"
                + "<tpNF>1</tpNF>"
                + "<IE>" + escaparXml(nfe.emitente().inscricaoEstadual()) + "</IE>"
                + destinatario(nfe.destinatario())
                + "<vNF>" + moeda(nfe.valorTotalNota()) + "</vNF>"
                + "<vICMS>" + moeda(nfe.valorTotalIcms()) + "</vICMS>"
                + "<vST>" + moeda(BigDecimal.ZERO) + "</vST>"
                + "</detEvento>"
                + "</infEvento>"
                + "</evento>";

        String eventoAssinado = assinaturaXmlService.assinar(eventoSemAssinatura, id, certificado);

        String idLote = String.valueOf(System.nanoTime() % 1_000_000_000L);
        String envEvento = "<envEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<idLote>" + idLote + "</idLote>"
                + eventoAssinado.replaceFirst("<\\?xml[^>]*\\?>", "")
                + "</envEvento>";

        String respostaXml = SoapClient.enviar(httpClient, url, NAMESPACE, COD_ORGAO_EPEC, "1.00", envEvento);

        return interpretar(respostaXml);
    }

    private String destinatario(Destinatario destinatario) {
        String tagDocumento = destinatario.ehPessoaJuridica() ? "CNPJ" : "CPF";
        String tagIe = destinatario.inscricaoEstadual() != null
                ? "<IE>" + escaparXml(destinatario.inscricaoEstadual()) + "</IE>"
                : "";
        return "<dest>"
                + "<UF>" + escaparXml(destinatario.endereco().uf()) + "</UF>"
                + "<" + tagDocumento + ">" + destinatario.documentoSemMascara() + "</" + tagDocumento + ">"
                + tagIe
                + "</dest>";
    }

    private String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String extrairCnpjDoCertificado(CertificadoCarregado certificado) {
        if (certificado.info().cnpj() != null) {
            return certificado.info().cnpj();
        }
        throw new SefazComunicacaoException("Nao foi possivel determinar o CNPJ do autor do evento a partir do certificado");
    }

    /** TDec_1302 (schema NF-e) exige 2 casas decimais fixas, mesmo para zero - notas isentas/sem ICMS destacado (FIS-76). */
    private String moeda(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private EpecResponse interpretar(String respostaXml) {
        Matcher matcherRetEvento = TAG_RET_EVENTO.matcher(respostaXml);
        String trecho = matcherRetEvento.find() ? matcherRetEvento.group() : respostaXml;

        Matcher matcherStat = TAG_CSTAT.matcher(trecho);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(trecho);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de EPEC sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        return EpecResponse.de(cStat, xMotivo);
    }
}
