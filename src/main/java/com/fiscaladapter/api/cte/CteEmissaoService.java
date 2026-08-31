package com.fiscaladapter.api.cte;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.cte.Cte;
import com.fiscaladapter.documento.cte.CteXmlGenerator;
import com.fiscaladapter.documento.cte.CteXsdValidator;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.numeracao.NumeracaoSequencialService;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.cte.CteAutorizacaoClient;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.rejeicao.CatalogoRejeicaoSefaz;
import com.fiscaladapter.sefaz.rejeicao.RejeicaoSefaz;
import org.springframework.stereotype.Service;

/**
 * Pipeline de emissao do CT-e (modelo 57, FIS-44): mapeamento -> certificado
 * -> chave/XML/assinatura -> validacao XSD -> autorizacao sincrona
 * (CTeRecepcaoSincV4, unico modo em uso desde 30/06/2024 - NT 2024.001) ->
 * numeracao -> retencao.
 *
 * Sem RVN aqui (RegraNegocioService, FIS-24, opera sobre NotaFiscalEletronica
 * - nao existe ainda um conjunto de regras de negocio proprio do CT-e; se
 * necessario, seria um card proprio, no mesmo espirito do FIS-24). Sem
 * contingencia automatica (SVC-RS/SVC-SP do CT-e) pelo mesmo motivo da
 * NFC-e (FIS-43): fora do escopo deste card, uma falha de comunicacao
 * propaga como erro (502) em vez de failover automatico.
 */
@Service
public class CteEmissaoService {

    private final CteRequestMapper mapper;
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;
    private final CteXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final CteXsdValidator xsdValidator;
    private final CteAutorizacaoClient autorizacaoClient;
    private final NumeracaoSequencialService numeracaoSequencialService;
    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    public CteEmissaoService(CteRequestMapper mapper, CertificadoEmissorService certificadoEmissorService,
                              ChaveAcessoService chaveAcessoService, CteXmlGenerator xmlGenerator,
                              AssinaturaXmlService assinaturaXmlService, CteXsdValidator xsdValidator,
                              CteAutorizacaoClient autorizacaoClient, NumeracaoSequencialService numeracaoSequencialService,
                              RetencaoDocumentoFiscalService retencaoDocumentoFiscalService) {
        this.mapper = mapper;
        this.certificadoEmissorService = certificadoEmissorService;
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.autorizacaoClient = autorizacaoClient;
        this.numeracaoSequencialService = numeracaoSequencialService;
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
    }

    public CteResponse processar(CtePedidoEmissaoRequest pedido, String clientId) {
        Cte cte = mapper.paraDominio(pedido);
        String uf = cte.identificacao().uf();

        CertificadoCarregado certificado = certificadoEmissorService.carregar(clientId, cte.emitente().cnpjSemMascara());

        String chaveAcesso = chaveAcessoService.gerar(uf, cte.identificacao().dataEmissao(),
                cte.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.CTE),
                cte.identificacao().serie(), cte.identificacao().numero(), 1);

        String xmlSemAssinatura = xmlGenerator.gerar(cte, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "CTe" + chaveAcesso, certificado);
        xsdValidator.validar(xmlAssinado);

        AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(xmlAssinado, uf, cte.identificacao().ambiente(), certificado);

        if (autorizacao.autorizada()) {
            numeracaoSequencialService.reservar(cte.emitente().cnpjSemMascara(), uf,
                    cte.identificacao().serie(), TipoDocumentoFiscal.CTE, cte.identificacao().numero());

            retencaoDocumentoFiscalService.arquivar(chaveAcesso, cte.emitente().cnpjSemMascara(),
                    TipoDocumentoFiscal.CTE, autorizacao.numeroProtocolo(), xmlAssinado, cte.identificacao().dataEmissao());
        }

        RejeicaoSefaz rejeicao = !autorizacao.autorizada()
                ? CatalogoRejeicaoSefaz.classificar(autorizacao.codigoStatus(), autorizacao.motivo())
                : null;

        return new CteResponse(chaveAcesso, xmlAssinado, autorizacao.autorizada(), autorizacao.codigoStatus(),
                autorizacao.motivo(), autorizacao.numeroProtocolo(),
                cte.notasFiscaisTransportadas().stream().map(n -> n.chaveAcesso()).toList(),
                rejeicao != null ? rejeicao.mensagem() : null, rejeicao != null ? rejeicao.categoria() : null);
    }
}
