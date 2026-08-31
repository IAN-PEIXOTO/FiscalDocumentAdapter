package com.fiscaladapter.api.cte;

import com.fiscaladapter.api.idempotencia.IdempotenciaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de emissao de CT-e (modelo 57, FIS-44) - schema proprio (CtePedidoEmissaoRequest), nao o da NFe. */
@RestController
public class CteController {

    private final CteEmissaoService cteEmissaoService;
    private final IdempotenciaService idempotenciaService;

    public CteController(CteEmissaoService cteEmissaoService, IdempotenciaService idempotenciaService) {
        this.cteEmissaoService = cteEmissaoService;
        this.idempotenciaService = idempotenciaService;
    }

    @PostMapping("/api/v1/cte")
    public ResponseEntity<CteResponse> emitir(@RequestBody @Valid CtePedidoEmissaoRequest documento,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               Authentication authentication) {
        String clientId = authentication.getName();
        CteResponse resposta = idempotenciaService.executar(clientId, idempotencyKey, CteResponse.class, () ->
                cteEmissaoService.processar(documento, clientId));

        return ResponseEntity.ok(resposta);
    }
}
