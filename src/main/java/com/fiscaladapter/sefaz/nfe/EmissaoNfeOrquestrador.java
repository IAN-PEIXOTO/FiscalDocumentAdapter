package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import org.springframework.stereotype.Component;

/**
 * Orquestra a emissao com resiliencia (FIS-7/FIS-37): tenta o endpoint
 * normal da UF do emitente algumas vezes (reenvio simples, mesmo XML
 * assinado); se todas as tentativas falharem por problema de comunicacao,
 * assume contingencia - gera uma NOVA chave/XML com tpEmis da SVC designada
 * para aquela UF, assina de novo (tpEmis faz parte do conteudo assinado) e
 * envia para o servidor de contingencia.
 *
 * Fora do escopo desta versao: EPEC (contingencia baseada em evento, usada
 * quando nem a SVC responde) e fila/retomada assincrona de transmissao
 * (haveria que persistir o estado "pendente de transmissao" em banco e ter
 * um worker retomando depois) - registrar como debito tecnico se a
 * indisponibilidade da SVC tambem precisar ser coberta.
 */
@Component
public class EmissaoNfeOrquestrador {

    private static final int TENTATIVAS_ENDPOINT_NORMAL = 2;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 2000;

    private final ChaveAcessoService chaveAcessoService;
    private final NfeXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final NfeXsdValidator xsdValidator;
    private final NfeAutorizacaoClient autorizacaoClient;

    public EmissaoNfeOrquestrador(ChaveAcessoService chaveAcessoService, NfeXmlGenerator xmlGenerator,
                                   AssinaturaXmlService assinaturaXmlService, NfeXsdValidator xsdValidator,
                                   NfeAutorizacaoClient autorizacaoClient) {
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.autorizacaoClient = autorizacaoClient;
    }

    public ResultadoEmissaoNfe emitir(NotaFiscalEletronica nfe, CertificadoCarregado certificado) {
        String uf = nfe.identificacao().uf();

        DocumentoPreparado normal = prepararDocumento(nfe, certificado, "1");

        SefazComunicacaoException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= TENTATIVAS_ENDPOINT_NORMAL; tentativa++) {
            try {
                AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(
                        normal.xmlAssinado(), uf, nfe.identificacao().ambiente(), certificado);
                return new ResultadoEmissaoNfe(normal.chaveAcesso(), normal.xmlAssinado(), autorizacao, false);
            } catch (SefazComunicacaoException e) {
                ultimaFalha = e;
                if (tentativa < TENTATIVAS_ENDPOINT_NORMAL) {
                    aguardar();
                }
            }
        }

        return tentarContingencia(nfe, certificado, uf, ultimaFalha);
    }

    private ResultadoEmissaoNfe tentarContingencia(NotaFiscalEletronica nfe, CertificadoCarregado certificado,
                                                     String uf, SefazComunicacaoException falhaOriginal) {
        ServicoContingenciaSvc svc = MapeamentoContingenciaSvc.svcPara(uf);
        DocumentoPreparado contingencia = prepararDocumento(nfe, certificado, svc.tpEmis());

        try {
            AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(
                    contingencia.xmlAssinado(), uf, svc.chaveEndpoint(), nfe.identificacao().ambiente(), certificado);
            return new ResultadoEmissaoNfe(contingencia.chaveAcesso(), contingencia.xmlAssinado(), autorizacao, true);
        } catch (SefazComunicacaoException falhaContingencia) {
            SefazComunicacaoException falhaFinal = new SefazComunicacaoException(
                    "Endpoint normal da UF " + uf + " e a contingencia " + svc.chaveEndpoint()
                            + " falharam. Ultimo erro: " + falhaContingencia.getMessage(), falhaContingencia);
            if (falhaOriginal != null) {
                falhaFinal.addSuppressed(falhaOriginal);
            }
            throw falhaFinal;
        }
    }

    private DocumentoPreparado prepararDocumento(NotaFiscalEletronica nfe, CertificadoCarregado certificado, String tpEmis) {
        String chaveAcesso = chaveAcessoService.gerar(
                nfe.identificacao().uf(),
                nfe.identificacao().dataEmissao(),
                nfe.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFE),
                nfe.identificacao().serie(),
                nfe.identificacao().numero(),
                Integer.parseInt(tpEmis)
        );

        String xmlSemAssinatura = xmlGenerator.gerar(nfe, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "NFe" + chaveAcesso, certificado);
        xsdValidator.validar(xmlAssinado);

        return new DocumentoPreparado(chaveAcesso, xmlAssinado);
    }

    private void aguardar() {
        try {
            Thread.sleep(ESPERA_ENTRE_TENTATIVAS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record DocumentoPreparado(String chaveAcesso, String xmlAssinado) {
    }
}
