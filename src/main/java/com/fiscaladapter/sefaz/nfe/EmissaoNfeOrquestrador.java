package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.observabilidade.MdcChaveAcesso;
import com.fiscaladapter.observabilidade.NfeEmissaoMetrics;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Orquestra a emissao com resiliencia (FIS-7/FIS-37): tenta o endpoint
 * normal da UF do emitente algumas vezes (reenvio simples, mesmo XML
 * assinado); se todas as tentativas falharem por problema de comunicacao,
 * assume contingencia - gera uma NOVA chave/XML com tpEmis da SVC designada
 * para aquela UF, assina de novo (tpEmis faz parte do conteudo assinado) e
 * envia para o servidor de contingencia. Se ate a SVC falhar, tenta o ultimo
 * recurso: EPEC (evento que libera a NFe provisoriamente, sem autorizacao
 * definitiva - ver NfeEpecClient e ResultadoEmissaoNfe.viaEpec).
 *
 * Fora do escopo desta versao: fila/retomada assincrona de transmissao da
 * NFe emitida via EPEC assim que a SEFAZ voltar (haveria que persistir o
 * estado "pendente de transmissao" em banco e ter um worker retomando
 * depois) - registrar como debito tecnico (ver FIS-30).
 */
@Component
public class EmissaoNfeOrquestrador {

    private static final Logger log = LoggerFactory.getLogger(EmissaoNfeOrquestrador.class);

    private static final int TENTATIVAS_ENDPOINT_NORMAL = 2;
    private static final long ESPERA_ENTRE_TENTATIVAS_MS = 2000;
    private static final String TP_EMIS_EPEC = "4";

    private final ChaveAcessoService chaveAcessoService;
    private final NfeXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final NfeXsdValidator xsdValidator;
    private final NfeAutorizacaoClient autorizacaoClient;
    private final NfeEpecClient epecClient;
    private final NfeEmissaoMetrics metrics;

    public EmissaoNfeOrquestrador(ChaveAcessoService chaveAcessoService, NfeXmlGenerator xmlGenerator,
                                   AssinaturaXmlService assinaturaXmlService, NfeXsdValidator xsdValidator,
                                   NfeAutorizacaoClient autorizacaoClient, NfeEpecClient epecClient,
                                   NfeEmissaoMetrics metrics) {
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.autorizacaoClient = autorizacaoClient;
        this.epecClient = epecClient;
        this.metrics = metrics;
    }

    public ResultadoEmissaoNfe emitir(NotaFiscalEletronica nfe, CertificadoCarregado certificado) {
        String uf = nfe.identificacao().uf();
        Timer.Sample cronometro = metrics.iniciarCronometro();

        DocumentoPreparado normal = prepararDocumento(nfe, certificado, "1");

        try (MdcChaveAcesso ignorado = MdcChaveAcesso.abrir(normal.chaveAcesso())) {
            log.info("Iniciando emissao de NFe para UF {}", uf);

            SefazComunicacaoException ultimaFalha = null;
            for (int tentativa = 1; tentativa <= TENTATIVAS_ENDPOINT_NORMAL; tentativa++) {
                try {
                    AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(
                            normal.xmlAssinado(), uf, nfe.identificacao().ambiente(), certificado);
                    return finalizarComSucesso(normal, autorizacao, false, cronometro);
                } catch (SefazComunicacaoException e) {
                    ultimaFalha = e;
                    log.warn("Falha de comunicacao com a SEFAZ da UF {} na tentativa {}/{}: {}",
                            uf, tentativa, TENTATIVAS_ENDPOINT_NORMAL, e.getMessage());
                    if (tentativa < TENTATIVAS_ENDPOINT_NORMAL) {
                        aguardar();
                    }
                }
            }

            return tentarContingencia(nfe, certificado, uf, ultimaFalha, cronometro);
        }
    }

    private ResultadoEmissaoNfe tentarContingencia(NotaFiscalEletronica nfe, CertificadoCarregado certificado,
                                                     String uf, SefazComunicacaoException falhaOriginal,
                                                     Timer.Sample cronometro) {
        ServicoContingenciaSvc svc = MapeamentoContingenciaSvc.svcPara(uf);
        log.warn("Endpoint normal da UF {} esgotou as tentativas - acionando contingencia {}", uf, svc.chaveEndpoint());
        DocumentoPreparado contingencia = prepararDocumento(nfe, certificado, svc.tpEmis());

        try (MdcChaveAcesso ignorado = MdcChaveAcesso.abrir(contingencia.chaveAcesso())) {
            AutorizacaoResponse autorizacao = autorizacaoClient.autorizar(
                    contingencia.xmlAssinado(), uf, svc.chaveEndpoint(), nfe.identificacao().ambiente(), certificado);
            return finalizarComSucesso(contingencia, autorizacao, true, cronometro);
        } catch (SefazComunicacaoException falhaContingencia) {
            log.warn("Contingencia {} tambem falhou - acionando EPEC como ultimo recurso. Erro: {}",
                    svc.chaveEndpoint(), falhaContingencia.getMessage());
            return tentarEpec(nfe, certificado, uf, falhaOriginal, falhaContingencia, cronometro);
        }
    }

    private ResultadoEmissaoNfe tentarEpec(NotaFiscalEletronica nfe, CertificadoCarregado certificado, String uf,
                                             SefazComunicacaoException falhaOriginal,
                                             SefazComunicacaoException falhaContingencia, Timer.Sample cronometro) {
        DocumentoPreparado epec = prepararDocumento(nfe, certificado, TP_EMIS_EPEC);

        try (MdcChaveAcesso ignorado = MdcChaveAcesso.abrir(epec.chaveAcesso())) {
            EpecResponse resposta = epecClient.registrar(nfe, epec.chaveAcesso(), nfe.identificacao().ambiente(), certificado);

            if (!resposta.registrada()) {
                throw new SefazComunicacaoException(
                        "EPEC nao registrado - cStat " + resposta.codigoStatus() + " (" + resposta.motivo() + ")");
            }

            metrics.registrarViaEpec(cronometro, resposta.codigoStatus());
            log.warn("Endpoint normal da UF {} e a contingencia falharam - NFe liberada provisoriamente via EPEC "
                    + "(protocolo definitivo pendente ate a retomada da transmissao normal, ver FIS-30)", uf);

            AutorizacaoResponse autorizacaoProvisoria = new AutorizacaoResponse(
                    resposta.codigoStatus(), resposta.motivo(), null, null, false);
            return new ResultadoEmissaoNfe(epec.chaveAcesso(), epec.xmlAssinado(), autorizacaoProvisoria, true, true);
        } catch (SefazComunicacaoException falhaEpec) {
            metrics.registrarErroComunicacao(cronometro);
            log.error("Endpoint normal da UF {}, contingencia e EPEC falharam. Ultimo erro: {}",
                    uf, falhaEpec.getMessage());

            SefazComunicacaoException falhaFinal = new SefazComunicacaoException(
                    "Endpoint normal da UF " + uf + ", contingencia e EPEC falharam. Ultimo erro: " + falhaEpec.getMessage(),
                    falhaEpec);
            falhaFinal.addSuppressed(falhaContingencia);
            if (falhaOriginal != null) {
                falhaFinal.addSuppressed(falhaOriginal);
            }
            throw falhaFinal;
        }
    }

    private ResultadoEmissaoNfe finalizarComSucesso(DocumentoPreparado documento, AutorizacaoResponse autorizacao,
                                                      boolean viaContingencia, Timer.Sample cronometro) {
        if (autorizacao.autorizada()) {
            metrics.registrarAutorizada(cronometro, viaContingencia);
            log.info("NFe autorizada pela SEFAZ (protocolo {}, contingencia={})",
                    autorizacao.numeroProtocolo(), viaContingencia);
        } else {
            metrics.registrarRejeitada(cronometro, viaContingencia, autorizacao.codigoStatus());
            log.warn("NFe rejeitada pela SEFAZ - cStat {} ({}), contingencia={}",
                    autorizacao.codigoStatus(), autorizacao.motivo(), viaContingencia);
        }
        return new ResultadoEmissaoNfe(documento.chaveAcesso(), documento.xmlAssinado(), autorizacao, viaContingencia, false);
    }

    private DocumentoPreparado prepararDocumento(NotaFiscalEletronica nfe, CertificadoCarregado certificado, String tpEmis) {
        // usa o tipoDocumento real (FIS-43) - antes tinha TipoDocumentoFiscal.NFE fixo, o que geraria
        // uma chave de acesso com mod=55 mesmo para um documento cujo XML tem mod=65 (NfeXmlGenerator
        // ja le tipoDocumento corretamente) se este orquestrador algum dia processasse NFC-e.
        String chaveAcesso = chaveAcessoService.gerar(
                nfe.identificacao().uf(),
                nfe.identificacao().dataEmissao(),
                nfe.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(nfe.identificacao().tipoDocumento()),
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
