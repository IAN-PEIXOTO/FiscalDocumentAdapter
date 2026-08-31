package com.fiscaladapter.api.nfce;

import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import com.fiscaladapter.api.nfe.NfeRequestMapper;
import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfce.NfceQrCodeService;
import com.fiscaladapter.documento.nfce.NfceQrCodeUrlRegistry;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.rvn.RegraNegocioService;
import com.fiscaladapter.numeracao.NumeracaoSequencialService;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.nfe.NfeAutorizacaoClient;
import com.fiscaladapter.sefaz.rejeicao.CatalogoRejeicaoSefaz;
import com.fiscaladapter.sefaz.rejeicao.RejeicaoSefaz;
import org.springframework.stereotype.Service;

/**
 * Pipeline de emissao da NFC-e (modelo 65, FIS-43): mapeamento -> RVN ->
 * certificado -> chave/XML/assinatura -> QR Code online -> validacao XSD ->
 * transmissao SINCRONA (indSinc=1) -> numeracao -> retencao. Reaproveita
 * quase todo o pipeline da NFe (NfeRequestMapper.paraDominio(pedido, NFCE) ja
 * existe desde o FIS-17, assim como NfeXmlGenerator/NfeXsdValidator, que ja
 * leem tipoDocumento para gerar mod=65 corretamente).
 *
 * NAO reusa EmissaoNfeOrquestrador: aquele orquestrador cai em contingencia
 * SVC-AN e, em ultimo caso, EPEC quando o endpoint normal falha - ambos
 * mecanismos EXCLUSIVOS da NFe. A contingencia especifica da NFC-e e o modo
 * offline (tpEmis=9, QR Code assinado localmente com a chave do proprio
 * emissor - ver NfceQrCodeService.gerarConteudoOffline, ja implementado
 * desde o FIS-17), que exige uma decisao explicita do PDV (nao pode ser
 * automatica silenciosa: implica guardar o XML localmente e retransmitir
 * depois) - fora do escopo deste card, registrado como debito tecnico
 * (mesma natureza do FIS-30 para o EPEC da NFe). Por isso, aqui, uma falha
 * de comunicacao com a SEFAZ propaga como erro (502) em vez de contingencia
 * automatica.
 */
@Service
public class NfceEmissaoService {

    private final NfeRequestMapper mapper;
    private final RegraNegocioService regraNegocioService;
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;
    private final NfeXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final NfeXsdValidator xsdValidator;
    private final NfceQrCodeService qrCodeService;
    private final NfceQrCodeUrlRegistry qrCodeUrlRegistry;
    private final NfeAutorizacaoClient autorizacaoClient;
    private final NumeracaoSequencialService numeracaoSequencialService;
    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    public NfceEmissaoService(NfeRequestMapper mapper, RegraNegocioService regraNegocioService,
                               CertificadoEmissorService certificadoEmissorService, ChaveAcessoService chaveAcessoService,
                               NfeXmlGenerator xmlGenerator, AssinaturaXmlService assinaturaXmlService,
                               NfeXsdValidator xsdValidator, NfceQrCodeService qrCodeService,
                               NfceQrCodeUrlRegistry qrCodeUrlRegistry, NfeAutorizacaoClient autorizacaoClient,
                               NumeracaoSequencialService numeracaoSequencialService,
                               RetencaoDocumentoFiscalService retencaoDocumentoFiscalService) {
        this.mapper = mapper;
        this.regraNegocioService = regraNegocioService;
        this.certificadoEmissorService = certificadoEmissorService;
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.qrCodeService = qrCodeService;
        this.qrCodeUrlRegistry = qrCodeUrlRegistry;
        this.autorizacaoClient = autorizacaoClient;
        this.numeracaoSequencialService = numeracaoSequencialService;
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
    }

    public NfceResponse processar(NfePedidoEmissaoRequest documento, String clientId) {
        NotaFiscalEletronica nfce = mapper.paraDominio(documento, TipoDocumentoFiscal.NFCE);

        // dest e opcional de proposito (FIS-17): NFC-e permite consumidor nao identificado (venda anonima).
        regraNegocioService.validar(nfce);

        CertificadoCarregado certificado =
                certificadoEmissorService.carregar(clientId, nfce.emitente().cnpjSemMascara());

        String uf = nfce.identificacao().uf();
        String chaveAcesso = chaveAcessoService.gerar(uf, nfce.identificacao().dataEmissao(),
                nfce.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFCE),
                nfce.identificacao().serie(), nfce.identificacao().numero(), 1);

        String xmlSemAssinatura = xmlGenerator.gerar(nfce, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "NFe" + chaveAcesso, certificado);

        String urlConsulta = qrCodeUrlRegistry.obterUrl(uf, nfce.identificacao().ambiente());
        String conteudoQrCode = qrCodeService.gerarConteudoOnline(chaveAcesso, nfce.identificacao().ambiente(), urlConsulta);
        String xmlComQrCode = qrCodeService.inserirInfNFeSupl(xmlAssinado, conteudoQrCode, urlConsulta);

        xsdValidator.validar(xmlComQrCode);

        AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(xmlComQrCode, uf, nfce.identificacao().ambiente(), certificado);

        if (autorizacao.autorizada()) {
            numeracaoSequencialService.reservar(nfce.emitente().cnpjSemMascara(), uf,
                    nfce.identificacao().serie(), TipoDocumentoFiscal.NFCE, nfce.identificacao().numero());

            retencaoDocumentoFiscalService.arquivar(chaveAcesso, nfce.emitente().cnpjSemMascara(),
                    TipoDocumentoFiscal.NFCE, autorizacao.numeroProtocolo(), xmlComQrCode, nfce.identificacao().dataEmissao());
        }

        RejeicaoSefaz rejeicao = !autorizacao.autorizada()
                ? CatalogoRejeicaoSefaz.classificar(autorizacao.codigoStatus(), autorizacao.motivo())
                : null;

        return new NfceResponse(chaveAcesso, xmlComQrCode, autorizacao.autorizada(), autorizacao.codigoStatus(),
                autorizacao.motivo(), autorizacao.numeroProtocolo(), conteudoQrCode, urlConsulta,
                rejeicao != null ? rejeicao.mensagem() : null, rejeicao != null ? rejeicao.categoria() : null);
    }
}
