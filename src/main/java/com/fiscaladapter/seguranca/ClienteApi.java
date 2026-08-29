package com.fiscaladapter.seguranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Credencial de um sistema cliente autorizado a consumir a API (FIS-15).
 * O segredo nunca e armazenado em texto puro - so os hashes (bcrypt).
 * secretHashSecundario permite rotacao sem downtime: durante a transicao,
 * tanto o segredo antigo quanto o novo sao aceitos.
 */
@Entity
@Table(name = "cliente_api")
public class ClienteApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, unique = true, length = 64)
    private String clientId;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(name = "secret_hash_primario", nullable = false, length = 200)
    private String secretHashPrimario;

    @Column(name = "secret_hash_secundario", length = 200)
    private String secretHashSecundario;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /** Secret usado para assinar (HMAC-SHA256) o corpo enviado ao webhook (FIS-31) - criptografado em repouso. */
    @Column(name = "webhook_secret_criptografado", length = 500)
    private String webhookSecretCriptografado;

    @Version
    private long version;

    protected ClienteApi() {
        // JPA
    }

    public ClienteApi(String clientId, String nome, String secretHashPrimario) {
        this.clientId = clientId;
        this.nome = nome;
        this.secretHashPrimario = secretHashPrimario;
        this.ativo = true;
        this.criadoEm = Instant.now();
    }

    /** Promove o segredo atual (novo) a primario e move o antigo para secundario, para rotacao sem downtime. */
    public void rotacionarSegredo(String novoSecretHash) {
        this.secretHashSecundario = this.secretHashPrimario;
        this.secretHashPrimario = novoSecretHash;
    }

    /** Encerra a janela de transicao: o segredo antigo deixa de ser aceito. */
    public void encerrarRotacao() {
        this.secretHashSecundario = null;
    }

    public void revogar() {
        this.ativo = false;
    }

    /** Webhook de notificacao de status (FIS-25) - null desativa notificacoes para este cliente. */
    public void definirWebhookUrl(String url) {
        this.webhookUrl = url;
    }

    /** Secret de assinatura HMAC do webhook (FIS-31), ja criptografado - gerado de novo a cada cadastro (rotaciona). */
    public void definirWebhookSecretCriptografado(String secretCriptografado) {
        this.webhookSecretCriptografado = secretCriptografado;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public String getWebhookSecretCriptografado() {
        return webhookSecretCriptografado;
    }

    /**
     * Combina os hashes validos em um unico valor, no formato aceito pelo
     * {@link DualHashPasswordEncoder}, para permitir dois segredos validos
     * simultaneamente durante a rotacao.
     */
    public String secretHashCombinado() {
        if (secretHashSecundario == null) {
            return secretHashPrimario;
        }
        return secretHashPrimario + DualHashPasswordEncoder.SEPARADOR + secretHashSecundario;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getNome() {
        return nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
