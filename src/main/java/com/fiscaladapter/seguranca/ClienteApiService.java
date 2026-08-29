package com.fiscaladapter.seguranca;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class ClienteApiService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteApiRepository repository;
    private final DualHashPasswordEncoder passwordEncoder;
    private final CriptografiaEmRepousoService criptografiaEmRepousoService;

    public ClienteApiService(ClienteApiRepository repository, DualHashPasswordEncoder passwordEncoder,
                              CriptografiaEmRepousoService criptografiaEmRepousoService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.criptografiaEmRepousoService = criptografiaEmRepousoService;
    }

    /** Cadastra um novo cliente e retorna o client_secret em texto puro (unica vez - nao e recuperavel depois). */
    @Transactional
    public CredenciaisGeradas cadastrar(String nome) {
        String clientId = "cli_" + UUID.randomUUID().toString().replace("-", "");
        String clientSecret = gerarSegredo();

        ClienteApi cliente = new ClienteApi(clientId, nome, passwordEncoder.encode(clientSecret));
        repository.save(cliente);

        return new CredenciaisGeradas(clientId, clientSecret);
    }

    /** Gera um novo secret e mantem o antigo valido ate encerrarRotacao ser chamado. */
    @Transactional
    public String rotacionarSegredo(String clientId) {
        ClienteApi cliente = buscarOuFalhar(clientId);
        String novoSegredo = gerarSegredo();
        cliente.rotacionarSegredo(passwordEncoder.encode(novoSegredo));
        return novoSegredo;
    }

    @Transactional
    public void encerrarRotacao(String clientId) {
        buscarOuFalhar(clientId).encerrarRotacao();
    }

    @Transactional
    public void revogar(String clientId) {
        buscarOuFalhar(clientId).revogar();
    }

    /**
     * Cadastra (ou recadastra - gera um novo secret a cada chamada) o webhook do cliente.
     * @return o secret em texto puro (FIS-31), para o cliente guardar e usar na validacao
     *         do HMAC das notificacoes - unica vez, nao e recuperavel depois (mesmo padrao
     *         do client_secret).
     */
    @Transactional
    public String definirWebhookUrl(String clientId, String url) {
        ClienteApi cliente = buscarOuFalhar(clientId);
        cliente.definirWebhookUrl(url);
        String secret = gerarSegredo();
        cliente.definirWebhookSecretCriptografado(criptografiaEmRepousoService.criptografar(secret));
        return secret;
    }

    public String obterWebhookUrl(String clientId) {
        return buscarOuFalhar(clientId).getWebhookUrl();
    }

    /** Descriptografa o secret do webhook para assinar a notificacao (WebhookNotifierService) - nunca exposto via API. */
    public String obterWebhookSecret(String clientId) {
        String criptografado = buscarOuFalhar(clientId).getWebhookSecretCriptografado();
        return criptografado != null ? criptografiaEmRepousoService.descriptografar(criptografado) : null;
    }

    private ClienteApi buscarOuFalhar(String clientId) {
        return repository.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente nao encontrado: " + clientId));
    }

    private String gerarSegredo() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record CredenciaisGeradas(String clientId, String clientSecret) {
    }
}
