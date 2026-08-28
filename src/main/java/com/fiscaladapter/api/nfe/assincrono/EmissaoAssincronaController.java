package com.fiscaladapter.api.nfe.assincrono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import com.fiscaladapter.api.nfe.NfeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Emissao de NFe assincrona, via fila (FIS-25): a chamada retorna 202
 * imediatamente com o id do job, sem esperar a SEFAZ responder.
 * EmissaoAssincronaWorker processa a fila em segundo plano e, ao concluir
 * (autorizada, rejeitada ou falhou), chama o webhook do cliente (ver
 * WebhookController) se um estiver cadastrado. O status tambem pode ser
 * consultado a qualquer momento via GET, sem depender do webhook chegar.
 */
@RestController
public class EmissaoAssincronaController {

    private final EmissaoAssincronaService service;
    private final ObjectMapper objectMapper;

    public EmissaoAssincronaController(EmissaoAssincronaService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/nfe/assincrono")
    public ResponseEntity<EmissaoEnfileiradaResponse> enfileirar(@RequestBody @Valid NfePedidoEmissaoRequest documento,
                                                                  @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                  Authentication authentication) {
        Long id = service.enfileirar(authentication.getName(), idempotencyKey, documento);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new EmissaoEnfileiradaResponse(id, StatusEmissaoAssincrona.PENDENTE));
    }

    @GetMapping("/api/v1/nfe/assincrono/{id}")
    public ResponseEntity<EmissaoAssincronaStatusResponse> consultar(@PathVariable Long id, Authentication authentication) {
        return service.consultar(authentication.getName(), id)
                .map(view -> ResponseEntity.ok(new EmissaoAssincronaStatusResponse(
                        view.id(), view.status(), desserializarResultado(view.resultadoJson()), view.erroMensagem())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private NfeResponse desserializarResultado(String resultadoJson) {
        if (resultadoJson == null) {
            return null;
        }
        try {
            return objectMapper.readValue(resultadoJson, NfeResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao desserializar resultado da emissao assincrona", e);
        }
    }
}
