package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.api.idempotencia.IdempotenciaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint de emissao de MDF-e (modelo 58, FIS-45) - schema proprio (MdfePedidoEmissaoRequest). */
@RestController
public class MdfeController {

    private final MdfeEmissaoService mdfeEmissaoService;
    private final IdempotenciaService idempotenciaService;

    public MdfeController(MdfeEmissaoService mdfeEmissaoService, IdempotenciaService idempotenciaService) {
        this.mdfeEmissaoService = mdfeEmissaoService;
        this.idempotenciaService = idempotenciaService;
    }

    @PostMapping("/api/v1/mdfe")
    public ResponseEntity<MdfeResponse> emitir(@RequestBody @Valid MdfePedidoEmissaoRequest documento,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                Authentication authentication) {
        String clientId = authentication.getName();
        MdfeResponse resposta = idempotenciaService.executar(clientId, idempotencyKey, MdfeResponse.class, () ->
                mdfeEmissaoService.processar(documento, clientId));

        return ResponseEntity.ok(resposta);
    }
}
