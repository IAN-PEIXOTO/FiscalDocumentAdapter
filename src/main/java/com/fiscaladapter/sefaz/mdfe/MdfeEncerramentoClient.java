package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.CodigoUfSefaz;
import com.fiscaladapter.documento.mdfe.MdfeEncerramentoXmlGenerator;
import com.fiscaladapter.documento.mdfe.MdfeEventoXsdValidator;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registra o Encerramento do MDF-e (fim de percurso, tpEvento=110112,
 * FIS-45/criterio de aceite 2) - reusa MdfeEncerramentoXmlGenerator
 * (documento/mdfe, ja implementado e testado desde o FIS-19: gerava so o
 * XML do evento, sem assinar/transmitir) e completa o que faltava: assinar
 * e enviar para o servico MDFeRecepcaoEvento (mesmo endpoint do
 * cancelamento).
 */
@Component
public class MdfeEncerramentoClient {

    private static final String NAMESPACE = "http://www.portalfiscal.inf.br/mdfe/wsdl/MDFeRecepcaoEvento";
    private static final String TP_EVENTO_ENCERRAMENTO = "110112";
    private static final String VERSAO_EVENTO = "3.00";
    private static final Pattern TAG_CSTAT = Pattern.compile("<cStat>(\\d+)</cStat>");
    private static final Pattern TAG_XMOTIVO = Pattern.compile("<xMotivo>(.*?)</xMotivo>");
    private static final Pattern TAG_RET_EVENTO = Pattern.compile("<retEventoMDFe.*?</retEventoMDFe>", Pattern.DOTALL);

    private final MdfeEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;
    private final AssinaturaXmlService assinaturaXmlService;
    private final MdfeEncerramentoXmlGenerator xmlGenerator;
    private final MdfeEventoXsdValidator xsdValidator;

    public MdfeEncerramentoClient(MdfeEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory,
                                   AssinaturaXmlService assinaturaXmlService, MdfeEncerramentoXmlGenerator xmlGenerator,
                                   MdfeEventoXsdValidator xsdValidator) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xmlGenerator = xmlGenerator;
        this.xsdValidator = xsdValidator;
    }

    public EncerramentoResponse encerrar(String chaveAcesso, String numeroProtocolo, String uf,
                                          String codigoMunicipioEncerramento, LocalDate dataEncerramento,
                                          TipoAmbiente ambiente, CertificadoCarregado certificado) {
        String url = endpointRegistry.obterUrl(uf, ambiente, TipoServicoMdfe.RECEPCAO_EVENTO);
        return encerrar(url, chaveAcesso, numeroProtocolo, uf, codigoMunicipioEncerramento, dataEncerramento,
                ambiente, certificado, httpClientFactory.criar(certificado));
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do endpoint real da SEFAZ. */
    EncerramentoResponse encerrar(String url, String chaveAcesso, String numeroProtocolo, String uf,
                                   String codigoMunicipioEncerramento, LocalDate dataEncerramento, TipoAmbiente ambiente,
                                   CertificadoCarregado certificado, HttpClient httpClient) {
        String cUF = CodigoUfSefaz.codigo(uf);
        String id = "ID" + TP_EVENTO_ENCERRAMENTO + chaveAcesso + "01";

        String eventoSemAssinatura = xmlGenerator.gerar(chaveAcesso, extrairCnpjDoCertificado(certificado), cUF,
                codigoMunicipioEncerramento, dataEncerramento, numeroProtocolo, ambiente);

        String eventoAssinado = assinaturaXmlService.assinar(eventoSemAssinatura, id, certificado);
        // FIS-98: valida o envelope local antes de enviar, mesmo padrao dos pipelines principais de
        // documento (CteEmissaoService/MdfeEmissaoService) - sem isso, um evento malformado (ex.:
        // assinatura ausente/malformada, campos obrigatorios do envelope faltando) so seria
        // detectado depois de uma ida e volta a SEFAZ. Nao usa validarEncerramento aqui porque essa
        // validacao de conteudo (TProt/TCodMunIBGE) e mais estrita que a validacao de
        // digitos-apenas ja feita em MdfeConsultaController - chamar aqui duplicaria a checagem
        // sem ganho real, e a cobertura do conteudo do evEncMDFe em si ja e feita por
        // MdfeEncerramentoXmlGeneratorTest.
        xsdValidator.validarEnvelope(eventoAssinado);
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

    private EncerramentoResponse interpretar(String respostaXml) {
        Matcher matcherRetEvento = TAG_RET_EVENTO.matcher(respostaXml);
        String trecho = matcherRetEvento.find() ? matcherRetEvento.group() : respostaXml;

        Matcher matcherStat = TAG_CSTAT.matcher(trecho);
        Matcher matcherMotivo = TAG_XMOTIVO.matcher(trecho);
        if (!matcherStat.find()) {
            throw new SefazComunicacaoException("Resposta de encerramento do MDF-e sem cStat: " + respostaXml);
        }
        String cStat = matcherStat.group(1);
        String xMotivo = matcherMotivo.find() ? matcherMotivo.group(1) : "";
        return EncerramentoResponse.de(cStat, xMotivo);
    }
}
