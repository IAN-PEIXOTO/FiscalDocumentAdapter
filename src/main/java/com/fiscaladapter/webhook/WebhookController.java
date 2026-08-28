package com.fiscaladapter.webhook;

import com.fiscaladapter.seguranca.ClienteApiService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro do webhook de notificacao de status (FIS-25): quando definido, o
 * adapter chama essa URL com POST sempre que uma emissao enfileirada (ver
 * EmissaoAssincronaController) e concluida (autorizada, rejeitada ou falhou).
 */
@RestController
public class WebhookController {

    private final ClienteApiService clienteApiService;

    public WebhookController(ClienteApiService clienteApiService) {
        this.clienteApiService = clienteApiService;
    }

    @PutMapping("/api/v1/webhook")
    public ResponseEntity<Void> definir(@RequestBody @Valid WebhookUrlRequest request, Authentication authentication) {
        clienteApiService.definirWebhookUrl(authentication.getName(), request.url());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/webhook")
    public ResponseEntity<WebhookUrlRequest> obter(Authentication authentication) {
        String url = clienteApiService.obterWebhookUrl(authentication.getName());
        if (url == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(new WebhookUrlRequest(url));
    }
}
