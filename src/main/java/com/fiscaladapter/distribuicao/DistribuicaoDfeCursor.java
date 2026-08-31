package com.fiscaladapter.distribuicao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Progresso da consulta incremental por NSU na NFeDistribuicaoDFe (FIS-40),
 * por CNPJ. A SEFAZ exige que cada consulta continue do ultimo NSU consumido
 * (nunca reinicie do zero) e penaliza consultas repetidas em curto intervalo
 * sem nada de novo (cStat 656 "Consumo Indevido") - consultadoEm existe para
 * o DistribuicaoDfeService aplicar um intervalo minimo entre consultas.
 */
@Entity
@Table(name = "distribuicao_dfe_cursor", uniqueConstraints = @UniqueConstraint(columnNames = {"cnpj"}))
public class DistribuicaoDfeCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 14)
    private String cnpj;

    @Column(name = "ultimo_nsu", nullable = false, length = 15)
    private String ultimoNsu = "000000000000000";

    @Column(name = "consultado_em")
    private Instant consultadoEm;

    protected DistribuicaoDfeCursor() {
        // JPA
    }

    public DistribuicaoDfeCursor(String cnpj) {
        this.cnpj = cnpj;
    }

    public void avancar(String novoUltimoNsu, Instant agora) {
        this.ultimoNsu = novoUltimoNsu;
        this.consultadoEm = agora;
    }

    public Long getId() {
        return id;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getUltimoNsu() {
        return ultimoNsu;
    }

    public Instant getConsultadoEm() {
        return consultadoEm;
    }
}
