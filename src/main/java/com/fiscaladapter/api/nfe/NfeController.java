package com.fiscaladapter.api.nfe;

import com.fiscaladapter.api.idempotencia.IdempotenciaService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.rvn.RegraNegocioService;
import com.fiscaladapter.sefaz.nfe.EmissaoNfeOrquestrador;
import com.fiscaladapter.sefaz.nfe.ResultadoEmissaoNfe;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Endpoint de recebimento de NFe. Nesta fase o certificado do emissor e
 * enviado junto na requisicao (multipart) porque o armazenamento seguro e
 * persistente de certificados ainda esta pendente (ver FIS-2/FIS-14); assim
 * que existir, este endpoint passa a buscar o certificado pelo emissor em
 * vez de recebe-lo a cada chamada.
 *
 * A geracao/assinatura/transmissao com retry e failover de contingencia
 * (FIS-7/FIS-37) fica em EmissaoNfeOrquestrador, nao aqui - o controller so
 * cuida de HTTP, autenticacao e idempotencia.
 */
@RestController
public class NfeController {

    private final NfeRequestMapper mapper;
    private final CertificadoDigitalService certificadoDigitalService;
    private final RegraNegocioService regraNegocioService;
    private final IdempotenciaService idempotenciaService;
    private final EmissaoNfeOrquestrador emissaoNfeOrquestrador;

    public NfeController(NfeRequestMapper mapper, CertificadoDigitalService certificadoDigitalService,
                          RegraNegocioService regraNegocioService, IdempotenciaService idempotenciaService,
                          EmissaoNfeOrquestrador emissaoNfeOrquestrador) {
        this.mapper = mapper;
        this.certificadoDigitalService = certificadoDigitalService;
        this.regraNegocioService = regraNegocioService;
        this.idempotenciaService = idempotenciaService;
        this.emissaoNfeOrquestrador = emissaoNfeOrquestrador;
    }

    @PostMapping(value = "/api/v1/nfe", consumes = "multipart/form-data")
    public ResponseEntity<NfeResponse> emitir(@RequestPart("documento") @Valid NfePedidoEmissaoRequest documento,
                                               @RequestPart("certificado") MultipartFile certificado,
                                               @RequestParam("senhaCertificado") String senhaCertificado,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               Authentication authentication) {
        NfeResponse resposta = idempotenciaService.executar(authentication.getName(), idempotencyKey, () ->
                processar(documento, certificado, senhaCertificado));

        return ResponseEntity.ok(resposta);
    }

    private NfeResponse processar(NfePedidoEmissaoRequest documento, MultipartFile certificado, String senhaCertificado) {
        NotaFiscalEletronica nfe = mapper.paraDominio(documento);

        regraNegocioService.validar(nfe);

        CertificadoCarregado certificadoCarregado;
        try {
            certificadoCarregado = certificadoDigitalService.carregar(
                    certificado.getInputStream(), senhaCertificado.toCharArray());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o arquivo de certificado enviado", e);
        }

        ResultadoEmissaoNfe resultado = emissaoNfeOrquestrador.emitir(nfe, certificadoCarregado);

        return new NfeResponse(resultado.chaveAcesso(), resultado.xmlAssinado(),
                resultado.autorizacao().autorizada(), resultado.autorizacao().codigoStatus(),
                resultado.autorizacao().motivo(), resultado.autorizacao().numeroProtocolo(),
                resultado.viaContingencia());
    }
}
