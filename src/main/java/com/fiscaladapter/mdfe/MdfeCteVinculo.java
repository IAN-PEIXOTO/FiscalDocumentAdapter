package com.fiscaladapter.mdfe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Registra que um CT-e foi manifestado (incluido) num MDF-e (FIS-61) - gravado no momento da
 * emissao do MDF-e (`MdfeEmissaoService`), a partir das chaves de CT-e que ele declara
 * transportar. Substitui a varredura O(n) que descriptografava TODOS os MDF-e arquivados de um
 * emissor a cada consulta/cancelamento de um unico CT-e
 * (`CteConsultaController.mdfeVinculado`, antes via `RetencaoDocumentoFiscalService.recuperarPorEmissorETipo`)
 * por uma consulta indexada por `chave_cte`.
 *
 * Um CT-e so pode estar vinculado a um unico MDF-e por vez na pratica (uma carga so viaja numa
 * viagem/manifesto por vez) - dai a constraint unica em `chave_cte`, nao em `chave_mdfe`.
 */
@Entity
@Table(
        name = "mdfe_cte_vinculo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chave_cte"})
)
public class MdfeCteVinculo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_cte", nullable = false, length = 44)
    private String chaveCte;

    @Column(name = "chave_mdfe", nullable = false, length = 44)
    private String chaveMdfe;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected MdfeCteVinculo() {
        // JPA
    }

    public MdfeCteVinculo(String chaveCte, String chaveMdfe, Instant criadoEm) {
        this.chaveCte = chaveCte;
        this.chaveMdfe = chaveMdfe;
        this.criadoEm = criadoEm;
    }

    public String getChaveCte() {
        return chaveCte;
    }

    public String getChaveMdfe() {
        return chaveMdfe;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }
}
