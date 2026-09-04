package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.api.AmbienteEmissaoValidator;
import com.fiscaladapter.api.ValidacaoParametros;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.FusoHorarioFiscal;
import com.fiscaladapter.documento.mdfe.Mdfe;
import com.fiscaladapter.documento.mdfe.MdfeXmlParser;
import com.fiscaladapter.documento.mdfe.damdfe.DadosImpressaoDamdfe;
import com.fiscaladapter.documento.mdfe.damdfe.DamdfeGenerator;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.mdfe.MdfeEncerramentoRegistroService;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.mdfe.EncerramentoResponse;
import com.fiscaladapter.sefaz.mdfe.MdfeCancelamentoClient;
import com.fiscaladapter.sefaz.mdfe.MdfeConsultaProtocoloClient;
import com.fiscaladapter.sefaz.mdfe.MdfeEncerramentoClient;
import com.fiscaladapter.sefaz.mdfe.MdfeJaEncerradoException;
import com.fiscaladapter.sefaz.mdfe.PrazoCancelamentoMdfeExpiradoException;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.sefaz.rejeicao.CatalogoRejeicaoSefaz;
import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;
import com.fiscaladapter.sefaz.rejeicao.RejeicaoSefaz;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Endpoints pos-emissao de MDF-e: consulta de situacao, encerramento (fim
 * de percurso, criterio de aceite 2) e cancelamento dentro do prazo legal
 * (criterio de aceite 3) - FIS-45. Mesmo padrao multi-tenant do
 * NfeConsultaController/CteConsultaController.
 */
@RestController
public class MdfeConsultaController {

    /** Ajuste SINIEF 21/2010 - cancelamento so vale se o transporte ainda nao comecou (nao verificavel localmente) (FIS-45). */
    private static final Duration PRAZO_CANCELAMENTO_MDFE = Duration.ofHours(24);

    private final MdfeConsultaProtocoloClient consultaProtocoloClient;
    private final MdfeCancelamentoClient cancelamentoClient;
    private final MdfeEncerramentoClient encerramentoClient;
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;
    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;
    private final DamdfeGenerator damdfeGenerator;
    private final MdfeEncerramentoRegistroService encerramentoRegistroService;
    private final AmbienteEmissaoValidator ambienteEmissaoValidator;

    public MdfeConsultaController(MdfeConsultaProtocoloClient consultaProtocoloClient,
                                   MdfeCancelamentoClient cancelamentoClient,
                                   MdfeEncerramentoClient encerramentoClient,
                                   CertificadoEmissorService certificadoEmissorService,
                                   ChaveAcessoService chaveAcessoService,
                                   RetencaoDocumentoFiscalService retencaoDocumentoFiscalService,
                                   DamdfeGenerator damdfeGenerator,
                                   MdfeEncerramentoRegistroService encerramentoRegistroService,
                                   AmbienteEmissaoValidator ambienteEmissaoValidator) {
        this.consultaProtocoloClient = consultaProtocoloClient;
        this.cancelamentoClient = cancelamentoClient;
        this.encerramentoClient = encerramentoClient;
        this.certificadoEmissorService = certificadoEmissorService;
        this.chaveAcessoService = chaveAcessoService;
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
        this.damdfeGenerator = damdfeGenerator;
        this.encerramentoRegistroService = encerramentoRegistroService;
        this.ambienteEmissaoValidator = ambienteEmissaoValidator;
    }

    @PostMapping("/api/v1/mdfe/{chaveAcesso}/consulta")
    public ResponseEntity<ConsultaMdfeResponse> consultar(@PathVariable String chaveAcesso,
                                                           @RequestParam String uf,
                                                           @RequestParam TipoAmbiente ambiente,
                                                           Authentication authentication) {
        ValidacaoParametros.exigirChaveDeAcessoValida(chaveAcesso);
        ambienteEmissaoValidator.validar(chaveAcesso, ambiente);
        CertificadoCarregado certificado = carregarCertificado(chaveAcesso, authentication);

        ConsultaProtocoloResponse resposta = consultaProtocoloClient.consultar(chaveAcesso, uf, ambiente, certificado);

        RejeicaoSefaz rejeicao = classificarSeNecessario(resposta.autorizada(), resposta.codigoStatus(), resposta.motivo());
        DocumentosVinculados vinculados = documentosVinculados(chaveAcesso);
        return ResponseEntity.ok(new ConsultaMdfeResponse(
                chaveAcesso, resposta.autorizada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo(),
                encerramentoRegistroService.estaEncerrado(chaveAcesso), vinculados.chavesCte(), vinculados.chavesNfe(),
                mensagem(rejeicao), categoria(rejeicao)));
    }

    /**
     * "Documentos vinculados" (FIS-54, criterio de aceite 3): extraidos do XML arquivado por este
     * adapter na emissao, via MdfeXmlParser (FIS-49) - a SEFAZ nao devolve essa lista na consulta
     * de situacao. Vazios se o MDF-e nao foi emitido por este adapter.
     */
    private DocumentosVinculados documentosVinculados(String chaveAcesso) {
        return retencaoDocumentoFiscalService.recuperar(chaveAcesso)
                .map(documento -> {
                    Mdfe mdfe = MdfeXmlParser.paraDominio(documento.xmlAssinado());
                    return new DocumentosVinculados(mdfe.chavesCteTransportados(), mdfe.chavesNfeTransportadas());
                })
                .orElse(new DocumentosVinculados(java.util.List.of(), java.util.List.of()));
    }

    private record DocumentosVinculados(java.util.List<String> chavesCte, java.util.List<String> chavesNfe) {
    }

    /**
     * Encerramento do manifesto - fim de percurso (criterio de aceite 2). O municipio de
     * encerramento e informado pelo chamador (pode diferir do municipio de descarga previsto na
     * emissao, se a viagem terminar em local diferente do planejado).
     */
    @PostMapping("/api/v1/mdfe/{chaveAcesso}/encerramento")
    public ResponseEntity<EncerramentoMdfeResponse> encerrar(@PathVariable String chaveAcesso,
                                                              @RequestParam String uf,
                                                              @RequestParam TipoAmbiente ambiente,
                                                              @RequestParam String numeroProtocolo,
                                                              @RequestParam String codigoMunicipioEncerramento,
                                                              @RequestParam(required = false) LocalDate dataEncerramento,
                                                              Authentication authentication) {
        ValidacaoParametros.exigirChaveDeAcessoValida(chaveAcesso);
        ValidacaoParametros.exigirSomenteDigitos(numeroProtocolo, "numeroProtocolo");
        ValidacaoParametros.exigirSomenteDigitos(codigoMunicipioEncerramento, "codigoMunicipioEncerramento");
        ambienteEmissaoValidator.validar(chaveAcesso, ambiente);
        CertificadoCarregado certificado = carregarCertificado(chaveAcesso, authentication);

        // FIS-92: usar o fuso fixo do Brasil (mesma classe de bug do FIS-84), nao o do JVM/SO -
        // senao, perto da virada do dia, um deploy em fuso diferente registraria o dia calendario
        // errado no encerramento do manifesto (campo com relevancia legal).
        LocalDate dataDeEncerramento = dataEncerramento != null ? dataEncerramento : LocalDate.now(FusoHorarioFiscal.BRASIL);
        EncerramentoResponse resposta = encerramentoClient.encerrar(chaveAcesso, numeroProtocolo, uf,
                codigoMunicipioEncerramento, dataDeEncerramento, ambiente, certificado);

        RejeicaoSefaz rejeicao = classificarSeNecessario(resposta.encerrado(), resposta.codigoStatus(), resposta.motivo());
        String damdfePdfBase64 = null;
        if (resposta.encerrado()) {
            encerramentoRegistroService.registrar(chaveAcesso, codigoMunicipioEncerramento, dataDeEncerramento);
            damdfePdfBase64 = gerarDamdfeDeEncerramento(chaveAcesso, numeroProtocolo, codigoMunicipioEncerramento, dataDeEncerramento);
        }

        return ResponseEntity.ok(new EncerramentoMdfeResponse(
                chaveAcesso, resposta.encerrado(), resposta.codigoStatus(), resposta.motivo(), damdfePdfBase64,
                mensagem(rejeicao), categoria(rejeicao)));
    }

    /**
     * Reimpressao do DAMDFE com a indicacao de encerramento (FIS-49, criterio de aceite 3) - o
     * unico dado disponivel neste endpoint e a chave de acesso, entao o Mdfe original e
     * reconstruido a partir do XML assinado ja arquivado na emissao (mesma tecnica do
     * `CteConsultaController.notasFiscaisTransportadas`, reaproveitar em vez de duplicar estado).
     */
    private String gerarDamdfeDeEncerramento(String chaveAcesso, String numeroProtocoloAutorizacao,
                                              String codigoMunicipioEncerramento, LocalDate dataEncerramento) {
        RetencaoDocumentoFiscalService.DocumentoRecuperado documento = retencaoDocumentoFiscalService.recuperar(chaveAcesso)
                .orElseThrow(() -> new IllegalStateException("MDF-e " + chaveAcesso + " nao encontrado no arquivamento legal"));

        Mdfe mdfe = MdfeXmlParser.paraDominio(documento.xmlAssinado());
        // FIS-104: mesma correcao do FIS-92 (que ficou so na linha 140 deste arquivo) - fuso fixo
        // do Brasil, nao o do JVM/SO, senao a "data/hora de autorizacao" reimpressa pode mostrar
        // hora deslocada (e, perto da meia-noite, ate dia calendario errado) em deploys fora de
        // Brasilia.
        OffsetDateTime dataHoraAutorizacao = documento.dataEmissao().atStartOfDay(FusoHorarioFiscal.BRASIL).toOffsetDateTime();

        DadosImpressaoDamdfe dados = DadosImpressaoDamdfe.deEncerramento(
                documento.numeroProtocolo(), dataHoraAutorizacao, codigoMunicipioEncerramento, dataEncerramento);

        byte[] pdf = damdfeGenerator.gerar(mdfe, chaveAcesso, dados);
        return Base64.getEncoder().encodeToString(pdf);
    }

    @PostMapping("/api/v1/mdfe/{chaveAcesso}/cancelamento")
    public ResponseEntity<CancelamentoMdfeResponse> cancelar(@PathVariable String chaveAcesso,
                                                              @RequestParam String uf,
                                                              @RequestParam TipoAmbiente ambiente,
                                                              @RequestParam String numeroProtocolo,
                                                              @RequestParam String justificativa,
                                                              Authentication authentication) {
        ValidacaoParametros.exigirChaveDeAcessoValida(chaveAcesso);
        ValidacaoParametros.exigirSomenteDigitos(numeroProtocolo, "numeroProtocolo");
        ambienteEmissaoValidator.validar(chaveAcesso, ambiente);
        CertificadoCarregado certificado = carregarCertificado(chaveAcesso, authentication);

        if (encerramentoRegistroService.estaEncerrado(chaveAcesso)) {
            throw new MdfeJaEncerradoException();
        }
        verificarPrazoDeCancelamento(chaveAcesso, uf, ambiente, certificado);

        CancelamentoResponse resposta = cancelamentoClient.cancelar(
                chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificado);

        RejeicaoSefaz rejeicao = classificarSeNecessario(resposta.cancelado(), resposta.codigoStatus(), resposta.motivo());
        return ResponseEntity.ok(new CancelamentoMdfeResponse(
                chaveAcesso, resposta.cancelado(), resposta.codigoStatus(), resposta.motivo(),
                mensagem(rejeicao), categoria(rejeicao)));
    }

    private void verificarPrazoDeCancelamento(String chaveAcesso, String uf, TipoAmbiente ambiente,
                                               CertificadoCarregado certificado) {
        ConsultaProtocoloResponse situacao = consultaProtocoloClient.consultar(chaveAcesso, uf, ambiente, certificado);
        if (situacao.dhRecbto() == null) {
            return;
        }

        Duration tempoDesdeAAutorizacao = Duration.between(OffsetDateTime.parse(situacao.dhRecbto()), OffsetDateTime.now());
        if (tempoDesdeAAutorizacao.compareTo(PRAZO_CANCELAMENTO_MDFE) > 0) {
            throw new PrazoCancelamentoMdfeExpiradoException(PRAZO_CANCELAMENTO_MDFE, tempoDesdeAAutorizacao);
        }
    }

    private CertificadoCarregado carregarCertificado(String chaveAcesso, Authentication authentication) {
        return certificadoEmissorService.carregar(authentication.getName(), chaveAcessoService.cnpjEmitente(chaveAcesso));
    }

    private RejeicaoSefaz classificarSeNecessario(boolean sucesso, String codigoStatus, String motivo) {
        return sucesso ? null : CatalogoRejeicaoSefaz.classificar(codigoStatus, motivo);
    }

    private String mensagem(RejeicaoSefaz rejeicao) {
        return rejeicao != null ? rejeicao.mensagem() : null;
    }

    private CategoriaErroSefaz categoria(RejeicaoSefaz rejeicao) {
        return rejeicao != null ? rejeicao.categoria() : null;
    }
}
