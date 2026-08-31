package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.mdfe.Mdfe;
import com.fiscaladapter.documento.mdfe.MdfeXmlGenerator;
import com.fiscaladapter.documento.mdfe.MdfeXsdValidator;
import com.fiscaladapter.documento.mdfe.damdfe.DadosImpressaoDamdfe;
import com.fiscaladapter.documento.mdfe.damdfe.DamdfeGenerator;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.numeracao.NumeracaoSequencialService;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.mdfe.MdfeAutorizacaoClient;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.rejeicao.CatalogoRejeicaoSefaz;
import com.fiscaladapter.sefaz.rejeicao.RejeicaoSefaz;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Pipeline de emissao do MDF-e (modelo 58, FIS-45): mapeamento ->
 * certificado -> chave/XML/assinatura -> validacao XSD -> autorizacao
 * sincrona (MDFeRecepcaoSinc, unico modo em uso desde 30/06/2024 - NT
 * 2024.001, mesma migracao do CT-e) -> numeracao -> retencao. Sem RVN
 * propria e sem contingencia automatica - mesma decisao do CT-e (FIS-44)
 * e da NFC-e (FIS-43).
 */
@Service
public class MdfeEmissaoService {

    private final MdfeRequestMapper mapper;
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;
    private final MdfeXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final MdfeXsdValidator xsdValidator;
    private final MdfeAutorizacaoClient autorizacaoClient;
    private final NumeracaoSequencialService numeracaoSequencialService;
    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;
    private final DamdfeGenerator damdfeGenerator;

    public MdfeEmissaoService(MdfeRequestMapper mapper, CertificadoEmissorService certificadoEmissorService,
                               ChaveAcessoService chaveAcessoService, MdfeXmlGenerator xmlGenerator,
                               AssinaturaXmlService assinaturaXmlService, MdfeXsdValidator xsdValidator,
                               MdfeAutorizacaoClient autorizacaoClient, NumeracaoSequencialService numeracaoSequencialService,
                               RetencaoDocumentoFiscalService retencaoDocumentoFiscalService, DamdfeGenerator damdfeGenerator) {
        this.mapper = mapper;
        this.certificadoEmissorService = certificadoEmissorService;
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.autorizacaoClient = autorizacaoClient;
        this.numeracaoSequencialService = numeracaoSequencialService;
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
        this.damdfeGenerator = damdfeGenerator;
    }

    public MdfeResponse processar(MdfePedidoEmissaoRequest pedido, String clientId) {
        Mdfe mdfe = mapper.paraDominio(pedido);
        String uf = mdfe.identificacao().uf();

        CertificadoCarregado certificado = certificadoEmissorService.carregar(clientId, mdfe.emitente().cnpjSemMascara());

        String chaveAcesso = chaveAcessoService.gerar(uf, mdfe.identificacao().dataEmissao(),
                mdfe.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.MDFE),
                mdfe.identificacao().serie(), mdfe.identificacao().numero(), 1);

        String xmlSemAssinatura = xmlGenerator.gerar(mdfe, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "MDFe" + chaveAcesso, certificado);
        xsdValidator.validar(xmlAssinado);

        AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(xmlAssinado, uf, mdfe.identificacao().ambiente(), certificado);

        if (autorizacao.autorizada()) {
            numeracaoSequencialService.reservar(mdfe.emitente().cnpjSemMascara(), uf,
                    mdfe.identificacao().serie(), TipoDocumentoFiscal.MDFE, mdfe.identificacao().numero());

            retencaoDocumentoFiscalService.arquivar(chaveAcesso, mdfe.emitente().cnpjSemMascara(),
                    TipoDocumentoFiscal.MDFE, autorizacao.numeroProtocolo(), xmlAssinado, mdfe.identificacao().dataEmissao());
        }

        RejeicaoSefaz rejeicao = !autorizacao.autorizada()
                ? CatalogoRejeicaoSefaz.classificar(autorizacao.codigoStatus(), autorizacao.motivo())
                : null;

        String damdfePdfBase64 = gerarDamdfeSePermitido(mdfe, chaveAcesso, autorizacao);

        return new MdfeResponse(chaveAcesso, xmlAssinado, autorizacao.autorizada(), autorizacao.codigoStatus(),
                autorizacao.motivo(), autorizacao.numeroProtocolo(), damdfePdfBase64,
                rejeicao != null ? rejeicao.mensagem() : null, rejeicao != null ? rejeicao.categoria() : null);
    }

    /** O DAMDFE so tem validade legal para acompanhar a viagem quando o MDF-e foi autorizado (FIS-49). */
    private String gerarDamdfeSePermitido(Mdfe mdfe, String chaveAcesso, AutorizacaoResponse autorizacao) {
        if (!autorizacao.autorizada()) {
            return null;
        }

        OffsetDateTime dataHoraAutorizacao = autorizacao.dhRecbto() != null
                ? OffsetDateTime.parse(autorizacao.dhRecbto())
                : null;

        DadosImpressaoDamdfe dados = DadosImpressaoDamdfe.deEmissao(autorizacao.numeroProtocolo(), dataHoraAutorizacao);

        byte[] pdf = damdfeGenerator.gerar(mdfe, chaveAcesso, dados);
        return Base64.getEncoder().encodeToString(pdf);
    }
}
