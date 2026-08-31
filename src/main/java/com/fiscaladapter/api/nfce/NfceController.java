package com.fiscaladapter.api.nfce;

import com.fiscaladapter.api.idempotencia.IdempotenciaService;
import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de emissao de NFC-e (modelo 65, FIS-43) - mesmo payload JSON da
 * NFe (NfePedidoEmissaoRequest; infNFe.dest opcional para consumidor nao
 * identificado). So suporta o modo SINCRONO (indSinc=1) - o unico
 * efetivamente usado na pratica para NFC-e (lote de um unico documento; a
 * SEFAZ rejeita indSinc=0 nesse caso desde a NT 2025.001/regra GAP03a-3),
 * entao nao ha um "modo assincrono com webhook" analogo ao
 * EmissaoAssincronaController da NFe para a NFC-e.
 */
@RestController
public class NfceController {

    private final NfceEmissaoService nfceEmissaoService;
    private final IdempotenciaService idempotenciaService;

    public NfceController(NfceEmissaoService nfceEmissaoService, IdempotenciaService idempotenciaService) {
        this.nfceEmissaoService = nfceEmissaoService;
        this.idempotenciaService = idempotenciaService;
    }

    @PostMapping("/api/v1/nfce")
    public ResponseEntity<NfceResponse> emitir(@RequestBody @Valid NfePedidoEmissaoRequest documento,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                Authentication authentication) {
        String clientId = authentication.getName();
        NfceResponse resposta = idempotenciaService.executar(clientId, idempotencyKey, NfceResponse.class, () ->
                nfceEmissaoService.processar(documento, clientId));

        return ResponseEntity.ok(resposta);
    }
}
