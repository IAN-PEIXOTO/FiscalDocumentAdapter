package com.fiscaladapter.api.nfe;

import com.fiscaladapter.api.idempotencia.IdempotenciaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de recebimento de NFe, no mesmo formato JSON da API ACBr (ver
 * NfePedidoEmissaoRequest). O certificado do emissor e resolvido pelo CNPJ
 * presente no proprio payload (infNFe.emit.CNPJ) a partir do que foi
 * registrado via POST /api/v1/certificados (FIS-2) - o cliente nao precisa
 * mais reenviar o .p12 a cada emissao.
 *
 * O pipeline de emissao (mapeamento, RVN, certificado, transmissao com
 * retry/contingencia, reserva de numero, DANFE) fica em NfeEmissaoService,
 * nao aqui - o controller so cuida de HTTP, autenticacao e idempotencia.
 * Esse e o modo SINCRONO (a chamada bloqueia ate a SEFAZ responder); para o
 * modo assincrono com notificacao via webhook ver
 * EmissaoAssincronaController (POST /api/v1/nfe/assincrono, FIS-25).
 */
@RestController
public class NfeController {

    private final NfeEmissaoService nfeEmissaoService;
    private final IdempotenciaService idempotenciaService;

    public NfeController(NfeEmissaoService nfeEmissaoService, IdempotenciaService idempotenciaService) {
        this.nfeEmissaoService = nfeEmissaoService;
        this.idempotenciaService = idempotenciaService;
    }

    @PostMapping("/api/v1/nfe")
    public ResponseEntity<NfeResponse> emitir(@RequestBody @Valid NfePedidoEmissaoRequest documento,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               Authentication authentication) {
        String clientId = authentication.getName();
        NfeResponse resposta = idempotenciaService.executar(clientId, idempotencyKey, () ->
                nfeEmissaoService.processar(documento, clientId));

        return ResponseEntity.ok(resposta);
    }
}
