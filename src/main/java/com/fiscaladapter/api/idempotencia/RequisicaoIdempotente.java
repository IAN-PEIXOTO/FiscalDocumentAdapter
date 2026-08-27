package com.fiscaladapter.api.idempotencia;

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
 * Registra requisicoes de emissao por chave de idempotencia (header
 * Idempotency-Key), por cliente. Enquanto PROCESSANDO, uma segunda tentativa
 * com a mesma chave recebe 409 (nao reprocessa nem espera). Depois de
 * CONCLUIDA, a mesma chave retorna a resposta ja armazenada ate expirar
 * (janela de 24h - ver IdempotenciaService.JANELA_VALIDADE).
 */
@Entity
@Table(
        name = "requisicao_idempotente",
        uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "chave"})
)
public class RequisicaoIdempotente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(nullable = false, length = 200)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRequisicaoIdempotente status;

    @Lob
    @Column(name = "resposta_json")
    private String respostaJson;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    protected RequisicaoIdempotente() {
        // JPA
    }

    RequisicaoIdempotente(String clientId, String chave, Instant criadoEm, Instant expiraEm) {
        this.clientId = clientId;
        this.chave = chave;
        this.status = StatusRequisicaoIdempotente.PROCESSANDO;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    void concluir(String respostaJson) {
        this.status = StatusRequisicaoIdempotente.CONCLUIDA;
        this.respostaJson = respostaJson;
    }

    boolean expirada(Instant referencia) {
        return referencia.isAfter(expiraEm);
    }

    public Long getId() {
        return id;
    }

    public StatusRequisicaoIdempotente getStatus() {
        return status;
    }

    public String getRespostaJson() {
        return respostaJson;
    }
}
