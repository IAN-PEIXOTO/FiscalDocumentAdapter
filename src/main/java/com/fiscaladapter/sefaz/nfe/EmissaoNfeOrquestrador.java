package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
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
    /** cStat 204 = "Duplicidade de NF-e" - a SEFAZ ja processou essa chave antes (ex.: reenvio apos timeout de rede). */
    private static final String CSTAT_DUPLICIDADE = "204";

    private final ChaveAcessoService chaveAcessoService;
    private final NfeXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final NfeXsdValidator xsdValidator;
    private final NfeAutorizacaoClient autorizacaoClient;
    private final NfeConsultaProtocoloClient consultaProtocoloClient;
    private final NfeEpecClient epecClient;
    private final NfeEmissaoMetrics metrics;

    public EmissaoNfeOrquestrador(ChaveAcessoService chaveAcessoService, NfeXmlGenerator xmlGenerator,
                                   AssinaturaXmlService assinaturaXmlService, NfeXsdValidator xsdValidator,
                                   NfeAutorizacaoClient autorizacaoClient, NfeConsultaProtocoloClient consultaProtocoloClient,
                                   NfeEpecClient epecClient, NfeEmissaoMetrics metrics) {
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.autorizacaoClient = autorizacaoClient;
        this.consultaProtocoloClient = consultaProtocoloClient;
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
                    autorizacao = recuperarProtocoloSeDuplicidade(
                            autorizacao, normal.chaveAcesso(), uf, uf, nfe.identificacao().ambiente(), certificado);
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
            autorizacao = recuperarProtocoloSeDuplicidade(
                    autorizacao, contingencia.chaveAcesso(), uf, svc.chaveEndpoint(), nfe.identificacao().ambiente(), certificado);
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

    /**
     * cStat 204 nao significa que o documento foi rejeitado - significa que a SEFAZ ja recebeu e
     * processou essa chave antes (tipicamente um reenvio deste orquestrador apos timeout de rede
     * numa tentativa anterior que na verdade foi autorizada). Nesse caso consulta a situacao real
     * da chave e, se autorizada, devolve sucesso com o protocolo verdadeiro em vez de reportar uma
     * rejeicao para um documento que ja e valido perante o fisco.
     *
     * FIS-101: chaveEndpoint precisa ser o MESMO usado na chamada de autorizar (uf no caminho
     * normal, svc.chaveEndpoint() em contingencia) - consultar sempre no endpoint da UF, mesmo
     * quando a autorizacao foi enviada ao SVC, mandava a consulta de recuperacao para um endpoint
     * provavelmente ainda fora do ar (motivo de ter acionado a contingencia), fazendo a
     * recuperacao falhar silenciosamente e manter a rejeicao (204) falsa.
     *
     * FIS-106: a mesma consulta tambem pode revelar que a chave, na verdade, foi DENEGADA (110/
     * 301/302) - nao autorizada, mas tambem nao uma rejeicao comum, ja que a SEFAZ ja consome
     * definitivamente aquele numero (ver AutorizacaoResponse.denegada(), FIS-100). Se devolvessemos
     * a resposta original (cStat 204) sem repassar o cStat/motivo reais da denegacao, o chamador
     * (NfeEmissaoService) nunca acionaria o arquivamento+reserva de numeracao do FIS-100 - o
     * documento voltaria a ficar sem numero reservado, reabrindo exatamente aquele bug so que
     * atraves deste caminho de recuperacao de duplicidade.
     */
    private AutorizacaoResponse recuperarProtocoloSeDuplicidade(AutorizacaoResponse autorizacao, String chaveAcesso,
                                                                  String uf, String chaveEndpoint, TipoAmbiente ambiente,
                                                                  CertificadoCarregado certificado) {
        if (!CSTAT_DUPLICIDADE.equals(autorizacao.codigoStatus())) {
            return autorizacao;
        }

        log.warn("SEFAZ respondeu cStat 204 (duplicidade) para a chave {} - consultando o protocolo real antes de reportar rejeicao", chaveAcesso);
        try {
            ConsultaProtocoloResponse situacao = consultaProtocoloClient.consultar(chaveAcesso, uf, chaveEndpoint, ambiente, certificado);
            if (situacao.autorizada()) {
                log.info("Duplicidade confirmada como NFe ja autorizada (protocolo {}) - recuperando sucesso", situacao.numeroProtocolo());
                return new AutorizacaoResponse(situacao.codigoStatus(), situacao.motivo(),
                        situacao.numeroProtocolo(), situacao.dhRecbto(), true);
            }
            if (situacao.denegada()) {
                log.info("Duplicidade (204) confirmada como uso denegado (cStat {}) - repassando a denegacao real em vez da duplicidade",
                        situacao.codigoStatus());
                return new AutorizacaoResponse(situacao.codigoStatus(), situacao.motivo(),
                        situacao.numeroProtocolo(), situacao.dhRecbto(), false);
            }
        } catch (SefazComunicacaoException falhaConsulta) {
            log.warn("Falha ao consultar o protocolo real apos cStat 204 para a chave {} - mantendo como rejeicao: {}",
                    chaveAcesso, falhaConsulta.getMessage());
        }
        return autorizacao;
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
