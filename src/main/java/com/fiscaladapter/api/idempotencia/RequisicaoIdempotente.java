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

import java.time.Duration;
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
        uniqueConstraints = @UniqueConstraint(columnNames = {"client_id", "tipo_operacao", "chave"})
)
public class RequisicaoIdempotente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    /** Discrimina o endpoint/tipo de resposta (ex.: "NfeResponse", "NfceResponse") - sem isso, a
     * mesma Idempotency-Key reusada entre endpoints diferentes colidiria (FIS-43). */
    @Column(name = "tipo_operacao", nullable = false, length = 50)
    private String tipoOperacao;

    @Column(nullable = false, length = 200)
    private String chave;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusRequisicaoIdempotente status;

    /** Contem o JSON da resposta criptografado (AES-256-GCM, ver CriptografiaEmRepousoService) - nao texto puro. */
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

    RequisicaoIdempotente(String clientId, String tipoOperacao, String chave, Instant criadoEm, Instant expiraEm) {
        this.clientId = clientId;
        this.tipoOperacao = tipoOperacao;
        this.chave = chave;
        this.status = StatusRequisicaoIdempotente.PROCESSANDO;
        this.criadoEm = criadoEm;
        this.expiraEm = expiraEm;
    }

    void concluir(String respostaCriptografada) {
        this.status = StatusRequisicaoIdempotente.CONCLUIDA;
        this.respostaJson = respostaCriptografada;
    }

    boolean expirada(Instant referencia) {
        return referencia.isAfter(expiraEm);
    }

    /**
     * Verdadeiro se a requisicao ainda esta PROCESSANDO ha mais tempo do que uma emissao
     * normal levaria (FIS-65) - sinal de que o processo caiu entre transmitir a SEFAZ e gravar
     * a resposta, deixando o registro preso indefinidamente (o status PROCESSANDO por si so
     * nao expira pela janela de 24h de {@link #expirada}, que so vale para o cache de resposta
     * de uma requisicao CONCLUIDA).
     */
    boolean processandoHaMuitoTempo(Instant referencia, Duration tempoLimiteProcessamento) {
        return referencia.isAfter(criadoEm.plus(tempoLimiteProcessamento));
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
