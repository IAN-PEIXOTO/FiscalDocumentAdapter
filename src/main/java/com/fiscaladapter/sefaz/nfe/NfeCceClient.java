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
 * Emite Carta de Correcao Eletronica (CC-e), via evento tpEvento=110110
 * (recepcaoEvento4). Estrutura conferida contra o XSD oficial
 * leiauteCCe_v1.00.xsd: o texto de xCondUso e um valor fixo enumerado no
 * schema, nao pode ser alterado.
 *
 * A CC-e NAO pode alterar valores, impostos ou as partes (emitente/
 * destinatario) do documento - isso e uma regra legal (Convenio S/N de
 * 15/12/1970), nao uma restricao tecnica do XML em si (o campo xCorrecao e
 * texto livre). Nao ha como validar programaticamente a intencao do usuario;
 * cabe a quem chama a API garantir que a correcao enviada e elegivel.
 */
@Component
public class NfeCceClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    private static final String TP_EVENTO_CCE = "110110";
    private static final String X_COND_USO = "A Carta de Correção é disciplinada pelo § 1º-A do art. 7º do Convênio "
            + "S/N, de 15 de dezembro de 1970 e pode ser utilizada para regularização de erro ocorrido na emissão de "
            + "documento fiscal, desde que o erro não esteja relacionado com: I - as variáveis que determinam o valor "
            + "do imposto tais como: base de cálculo, alíquota, diferença de preço, quantidade, valor da operação ou "
            + "da prestação; II - a correção de dados cadastrais que implique mudança do remetente ou do "
            + "destinatário; III - a data de emissão ou de saída.";
    private static final DateTimeFormatter DATA_EVENTO_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_NPROT = Pattern.compile("<nProt>(\\d+)</nProt>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEvento.*?</retEvento>", Pattern.DOTALL);

    private final SefazEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;

    public NfeCceClient(SefazEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                         AssinaturaXmlService assinaturaXmlService) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
    }

    public CceResponse corrigir(String chaveAcesso, int numeroSequencial, String textoCorrecao, String uf,
                                 TipoAmbiente ambiente, CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoSefaz.RECEPCAO_EVENTO);
        return corrigir(url, chaveAcesso, numeroSequencial, textoCorrecao, uf, ambiente, certificado,
                httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    CceResponse corrigir(String url, String chaveAcesso, int numeroSequencial, String textoCorrecao, String uf,
                          TipoAmbiente ambiente, CertificadoCarregado certificado, HttpClient httpClient) {
        if (textoCorrecao == null || textoCorrecao.isBlank() || textoCorrecao.length() < 15) {
            throw new IllegalArgumentException("Texto da correcao deve ter pelo menos 15 caracteres");
        }
        if (numeroSequencial < 1 || numeroSequencial > 20) {
            throw new IllegalArgumentException("Numero sequencial da CC-e deve estar entre 1 e 20");
        }

        String cUF = CodigoUfSefaz.codigo(uf);
        String nSeqEvento = String.format("%02d", numeroSequencial);
        String id = "ID" + TP_EVENTO_CCE + chaveAcesso + nSeqEvento;

        String eventoSemAssinatura = "<evento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                + "<infEvento Id=\"" + id + "\">"
                + "<cOrgao>" + cUF + "</cOrgao>"
                + "<tpAmb>" + ambiente.codigo() + "</tpAmb>"
                + "<CNPJ>" + extrairCnpjDoCertificado(certificado) + "</CNPJ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + OffsetDateTime.now().format(DATA_EVENTO_FORMAT) + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_CCE + "</tpEvento>"
                + "<nSeqEvento>" + numeroSequencial + "</nSeqEvento>"
                + "<verEvento>1.00</verEvento>"
                + "<detEvento versao=\"1.00\">"
                + "<descEvento>Carta de Correção</descEvento>"
                + "<xCorrecao>" + escaparXml(textoCorrecao) + "</xCorrecao>"
                + "<xCondUso>" + escaparXml(X_COND_USO) + "</xCondUso>"
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

    private CceResponse interpretar(String respostaXml) {
        Matcher matcherRetEvento = TAG_RET_EVENTO.matcher(respostaXml);
        String trecho = matcherRetEvento.find() ? matcherRetEvento.group() : respostaXml;

        Matcher matcherStat = TAG_CSTAT.matcher(trecho);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(trecho);
        Matcher matcherProt = TAG_NPROT.matcher(trecho);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de CC-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        String nProt = matcherProt.find() ? matcherProt.group(1) : null;
        return CceResponse.de(cStat, xMotivo, nProt);
    }
}
