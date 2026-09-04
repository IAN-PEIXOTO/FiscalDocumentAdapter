package com.fiscaladapter.api.cte;

import com.fiscaladapter.api.ValidacaoParametros;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.cte.CteCancelamentoClient;
import com.fiscaladapter.sefaz.cte.CteConsultaProtocoloClient;
import com.fiscaladapter.sefaz.cte.CteJaManifestadoEmMdfeException;
import com.fiscaladapter.sefaz.cte.PrazoCancelamentoCteExpiradoException;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Endpoints pos-emissao de CT-e: consulta de situacao (com vinculo das
 * NF-e transportadas, criterio de aceite 3) e cancelamento dentro do prazo
 * legal (FIS-44). Mesmo padrao multi-tenant do NfeConsultaController -
 * certificado resolvido pelo CNPJ do emitente, extraido da propria chave
 * de acesso.
 */
@RestController
public class CteConsultaController {

    private static final Pattern TAG_CHAVE_NFE = Pattern.compile("<infNFe>\\s*<chave>(\\d{44})</chave>\\s*</infNFe>");
    private static final Pattern TAG_CHAVE_CTE_NO_MDFE = Pattern.compile("<infCTe>\\s*<chCTe>(\\d{44})</chCTe>\\s*</infCTe>");
    /** Ajuste SINIEF 09/07, clausula 14 - algumas UFs adotam prazo menor (FIS-44). */
    private static final Duration PRAZO_CANCELAMENTO_CTE = Duration.ofHours(168);

    private final CteConsultaProtocoloClient consultaProtocoloClient;
    private final CteCancelamentoClient cancelamentoClient;
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;
    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    public CteConsultaController(CteConsultaProtocoloClient consultaProtocoloClient,
                                  CteCancelamentoClient cancelamentoClient,
                                  CertificadoEmissorService certificadoEmissorService,
                                  ChaveAcessoService chaveAcessoService,
                                  RetencaoDocumentoFiscalService retencaoDocumentoFiscalService) {
        this.consultaProtocoloClient = consultaProtocoloClient;
        this.cancelamentoClient = cancelamentoClient;
        this.certificadoEmissorService = certificadoEmissorService;
        this.chaveAcessoService = chaveAcessoService;
        this.retencaoDocumentoFiscalService = retencaoDocumentoFiscalService;
    }

    @PostMapping("/api/v1/cte/{chaveAcesso}/consulta")
    public ResponseEntity<ConsultaCteResponse> consultar(@PathVariable String chaveAcesso,
                                                           @RequestParam String uf,
                                                           @RequestParam TipoAmbiente ambiente,
                                                           Authentication authentication) {
        CertificadoCarregado certificado = carregarCertificado(chaveAcesso, authentication);

        ConsultaProtocoloResponse resposta = consultaProtocoloClient.consultar(chaveAcesso, uf, ambiente, certificado);

        RejeicaoSefaz rejeicao = classificarSeNecessario(resposta.autorizada(), resposta.codigoStatus(), resposta.motivo());
        return ResponseEntity.ok(new ConsultaCteResponse(
                chaveAcesso, resposta.autorizada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo(),
                notasFiscaisTransportadas(chaveAcesso), mdfeVinculado(chaveAcesso), mensagem(rejeicao), categoria(rejeicao)));
    }

    @PostMapping("/api/v1/cte/{chaveAcesso}/cancelamento")
    public ResponseEntity<CancelamentoCteResponse> cancelar(@PathVariable String chaveAcesso,
                                                             @RequestParam String uf,
                                                             @RequestParam TipoAmbiente ambiente,
                                                             @RequestParam String numeroProtocolo,
                                                             @RequestParam String justificativa,
                                                             Authentication authentication) {
        ValidacaoParametros.exigirSomenteDigitos(numeroProtocolo, "numeroProtocolo");
        CertificadoCarregado certificado = carregarCertificado(chaveAcesso, authentication);

        verificarPrazoDeCancelamento(chaveAcesso, uf, ambiente, certificado);
        verificarSeJaManifestadoEmMdfe(chaveAcesso);

        CancelamentoResponse resposta = cancelamentoClient.cancelar(
                chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificado);

        RejeicaoSefaz rejeicao = classificarSeNecessario(resposta.cancelado(), resposta.codigoStatus(), resposta.motivo());
        return ResponseEntity.ok(new CancelamentoCteResponse(
                chaveAcesso, resposta.cancelado(), resposta.codigoStatus(), resposta.motivo(),
                mensagem(rejeicao), categoria(rejeicao)));
    }

    /**
     * Vinculo com os documentos transportados (criterio de aceite 3): extraido do XML arquivado
     * por este adapter na emissao (RetencaoDocumentoFiscalService, FIS-26/34) - a SEFAZ nao
     * devolve essa lista na consulta de situacao. Vazia se o CT-e nao foi emitido por este
     * adapter ou nao transportava nenhuma NF-e.
     */
    private List<String> notasFiscaisTransportadas(String chaveAcesso) {
        return retencaoDocumentoFiscalService.recuperar(chaveAcesso)
                .map(documento -> {
                    Matcher matcher = TAG_CHAVE_NFE.matcher(documento.xmlAssinado());
                    return matcher.results().map(r -> r.group(1)).toList();
                })
                .orElse(List.of());
    }

    /**
     * "Eventos vinculados" (FIS-53, criterio de aceite 1): varre os MDF-e ja arquivados por este
     * adapter para o mesmo CNPJ emissor do CT-e (a transportadora e sempre a mesma nos dois
     * documentos) procurando uma referencia a chave deste CT-e em infCTe/chCTe. A SEFAZ nao
     * devolve esse vinculo na consulta de situacao do CT-e - so o proprio MDF-e sabe quais CT-e
     * ele transporta.
     */
    private String mdfeVinculado(String chaveCte) {
        String cnpjEmissor = chaveAcessoService.cnpjEmitente(chaveCte);
        return retencaoDocumentoFiscalService.recuperarPorEmissorETipo(cnpjEmissor, TipoDocumentoFiscal.MDFE).stream()
                .filter(mdfe -> TAG_CHAVE_CTE_NO_MDFE.matcher(mdfe.xmlAssinado()).results()
                        .anyMatch(r -> r.group(1).equals(chaveCte)))
                .map(RetencaoDocumentoFiscalService.DocumentoRecuperado::chaveAcesso)
                .findFirst()
                .orElse(null);
    }

    /**
     * Bloqueia o cancelamento se algum MDF-e ja manifestou este CT-e para transporte (FIS-53,
     * criterio de aceite 2) - ver limitacao documentada em CteJaManifestadoEmMdfeException.
     */
    private void verificarSeJaManifestadoEmMdfe(String chaveCte) {
        String chaveMdfe = mdfeVinculado(chaveCte);
        if (chaveMdfe != null) {
            throw new CteJaManifestadoEmMdfeException(chaveMdfe);
        }
    }

    /**
     * CT-e so pode ser cancelado dentro do prazo legal (FIS-44) - consulta a SEFAZ para saber a
     * data/hora real de autorizacao e bloqueia preventivamente se o prazo ja passou, em vez de
     * gastar uma tentativa que a SEFAZ rejeitaria de qualquer forma. Se a consulta nao trouxer a
     * data de autorizacao (caso raro), deixa a SEFAZ decidir no proprio cancelamento.
     */
    private void verificarPrazoDeCancelamento(String chaveAcesso, String uf, TipoAmbiente ambiente,
                                               CertificadoCarregado certificado) {
        ConsultaProtocoloResponse situacao = consultaProtocoloClient.consultar(chaveAcesso, uf, ambiente, certificado);
        if (situacao.dhRecbto() == null) {
            return;
        }

        Duration tempoDesdeAAutorizacao = Duration.between(OffsetDateTime.parse(situacao.dhRecbto()), OffsetDateTime.now());
        if (tempoDesdeAAutorizacao.compareTo(PRAZO_CANCELAMENTO_CTE) > 0) {
            throw new PrazoCancelamentoCteExpiradoException(PRAZO_CANCELAMENTO_CTE, tempoDesdeAAutorizacao);
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
