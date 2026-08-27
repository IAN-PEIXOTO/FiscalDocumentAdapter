package com.fiscaladapter.api.nfe;

import com.fiscaladapter.api.idempotencia.IdempotenciaService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.danfe.DadosImpressaoDanfe;
import com.fiscaladapter.documento.nfe.danfe.DanfeGenerator;
import com.fiscaladapter.documento.nfe.danfe.OrientacaoDanfe;
import com.fiscaladapter.documento.nfe.rvn.RegraNegocioService;
import com.fiscaladapter.sefaz.nfe.EmissaoNfeOrquestrador;
import com.fiscaladapter.sefaz.nfe.ResultadoEmissaoNfe;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Endpoint de recebimento de NFe, no mesmo formato JSON da API ACBr (ver
 * NfePedidoEmissaoRequest). O certificado do emissor e resolvido pelo CNPJ
 * presente no proprio payload (infNFe.emit.CNPJ) a partir do que foi
 * registrado via POST /api/v1/certificados (FIS-2) - o cliente nao precisa
 * mais reenviar o .p12 a cada emissao.
 *
 * A geracao/assinatura/transmissao com retry e failover de contingencia
 * (FIS-7/FIS-37) fica em EmissaoNfeOrquestrador, nao aqui - o controller so
 * cuida de HTTP, autenticacao, resolucao do certificado, idempotencia e
 * geracao do DANFE (FIS-8) para devolver junto com o resultado da emissao.
 */
@RestController
public class NfeController {

    private final NfeRequestMapper mapper;
    private final CertificadoEmissorService certificadoEmissorService;
    private final RegraNegocioService regraNegocioService;
    private final IdempotenciaService idempotenciaService;
    private final EmissaoNfeOrquestrador emissaoNfeOrquestrador;
    private final DanfeGenerator danfeGenerator;

    public NfeController(NfeRequestMapper mapper, CertificadoEmissorService certificadoEmissorService,
                          RegraNegocioService regraNegocioService, IdempotenciaService idempotenciaService,
                          EmissaoNfeOrquestrador emissaoNfeOrquestrador, DanfeGenerator danfeGenerator) {
        this.mapper = mapper;
        this.certificadoEmissorService = certificadoEmissorService;
        this.regraNegocioService = regraNegocioService;
        this.idempotenciaService = idempotenciaService;
        this.emissaoNfeOrquestrador = emissaoNfeOrquestrador;
        this.danfeGenerator = danfeGenerator;
    }

    @PostMapping("/api/v1/nfe")
    public ResponseEntity<NfeResponse> emitir(@RequestBody @Valid NfePedidoEmissaoRequest documento,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               Authentication authentication) {
        NfeResponse resposta = idempotenciaService.executar(authentication.getName(), idempotencyKey, () ->
                processar(documento));

        return ResponseEntity.ok(resposta);
    }

    private NfeResponse processar(NfePedidoEmissaoRequest documento) {
        NotaFiscalEletronica nfe = mapper.paraDominio(documento);

        regraNegocioService.validar(nfe);

        CertificadoCarregado certificadoCarregado = certificadoEmissorService.carregar(nfe.emitente().cnpjSemMascara());

        ResultadoEmissaoNfe resultado = emissaoNfeOrquestrador.emitir(nfe, certificadoCarregado);

        return new NfeResponse(resultado.chaveAcesso(), resultado.xmlAssinado(),
                resultado.autorizacao().autorizada(), resultado.autorizacao().codigoStatus(),
                resultado.autorizacao().motivo(), resultado.autorizacao().numeroProtocolo(),
                resultado.viaContingencia(), resultado.viaEpec(), gerarDanfeSePermitido(nfe, resultado));
    }

    /**
     * O DANFE so tem validade legal para acompanhar a mercadoria quando a NFe
     * foi autorizada ou ao menos liberada provisoriamente via EPEC - nao faz
     * sentido (e seria enganoso) gerar o documento para uma nota rejeitada.
     */
    private String gerarDanfeSePermitido(NotaFiscalEletronica nfe, ResultadoEmissaoNfe resultado) {
        if (!resultado.autorizacao().autorizada() && !resultado.viaEpec()) {
            return null;
        }

        OffsetDateTime dataHoraAutorizacao = resultado.autorizacao().dhRecbto() != null
                ? OffsetDateTime.parse(resultado.autorizacao().dhRecbto())
                : null;

        DadosImpressaoDanfe dados = new DadosImpressaoDanfe(
                OrientacaoDanfe.RETRATO,
                resultado.viaContingencia() || resultado.viaEpec(),
                resultado.autorizacao().numeroProtocolo(),
                dataHoraAutorizacao);

        byte[] pdf = danfeGenerator.gerar(nfe, resultado.chaveAcesso(), dados);
        return Base64.getEncoder().encodeToString(pdf);
    }
}
