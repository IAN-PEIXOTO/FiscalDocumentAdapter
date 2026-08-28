package com.fiscaladapter.api.nfe.assincrono;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Um pedido de emissao de NFe enfileirado para processamento assincrono
 * (FIS-25). pedidoJson/resultadoJson vao criptografados (AES-256-GCM, ver
 * CriptografiaEmRepousoService) porque contem dados fiscais sensiveis do
 * emitente/destinatario, mesmo padrao ja usado por RequisicaoIdempotente.
 */
@Entity
@Table(
        name = "emissao_assincrona",
        uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "idempotency_key"})
)
public class EmissaoAssincrona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusEmissaoAssincrona status;

    @Lob
    @Column(name = "pedido_json", nullable = false)
    private String pedidoJson;

    @Lob
    @Column(name = "resultado_json")
    private String resultadoJson;

    @Column(name = "erro_mensagem", length = 2000)
    private String erroMensagem;

    @Column(name = "tentativas_notificacao", nullable = false)
    private int tentativasNotificacao;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected EmissaoAssincrona() {
        // JPA
    }

    public EmissaoAssincrona(String clientId, String idempotencyKey, String pedidoJson, Instant agora) {
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
        this.pedidoJson = pedidoJson;
        this.status = StatusEmissaoAssincrona.PENDENTE;
        this.tentativasNotificacao = 0;
        this.criadoEm = agora;
        this.atualizadoEm = agora;
    }

    public void marcarProcessando(Instant agora) {
        this.status = StatusEmissaoAssincrona.PROCESSANDO;
        this.atualizadoEm = agora;
    }

    public void concluir(String resultadoJsonCriptografado, Instant agora) {
        this.status = StatusEmissaoAssincrona.CONCLUIDA;
        this.resultadoJson = resultadoJsonCriptografado;
        this.atualizadoEm = agora;
    }

    public void falhar(String mensagemErro, Instant agora) {
        this.status = StatusEmissaoAssincrona.FALHA;
        this.erroMensagem = mensagemErro;
        this.atualizadoEm = agora;
    }

    public void incrementarTentativaNotificacao(Instant agora) {
        this.tentativasNotificacao++;
        this.atualizadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public String getClientId() {
        return clientId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public StatusEmissaoAssincrona getStatus() {
        return status;
    }

    public String getPedidoJson() {
        return pedidoJson;
    }

    public String getResultadoJson() {
        return resultadoJson;
    }

    public String getErroMensagem() {
        return erroMensagem;
    }

    public int getTentativasNotificacao() {
        return tentativasNotificacao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
