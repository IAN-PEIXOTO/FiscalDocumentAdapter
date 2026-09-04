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
 * Cadastro do webhook de notificacao de status (FIS-25/FIS-31): quando
 * definido, o adapter chama essa URL com POST sempre que uma emissao
 * enfileirada (ver EmissaoAssincronaController) e concluida (autorizada,
 * rejeitada ou falhou). Cada cadastro gera um novo secret de assinatura
 * HMAC-SHA256, devolvido em texto puro so nesta resposta - guarde-o para
 * validar a assinatura das notificacoes recebidas (header
 * X-Fiscaladapter-Signature: sha256=&lt;hex&gt;).
 */
@RestController
public class WebhookController {

    private final ClienteApiService clienteApiService;
    private final WebhookUrlValidator webhookUrlValidator;

    public WebhookController(ClienteApiService clienteApiService, WebhookUrlValidator webhookUrlValidator) {
        this.clienteApiService = clienteApiService;
        this.webhookUrlValidator = webhookUrlValidator;
    }

    @PutMapping("/api/v1/webhook")
    public ResponseEntity<WebhookCadastradoResponse> definir(@RequestBody @Valid WebhookUrlRequest request,
                                                              Authentication authentication) {
        webhookUrlValidator.validar(request.url());
        String secret = clienteApiService.definirWebhookUrl(authentication.getName(), request.url());
        return ResponseEntity.ok(new WebhookCadastradoResponse(request.url(), secret));
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
