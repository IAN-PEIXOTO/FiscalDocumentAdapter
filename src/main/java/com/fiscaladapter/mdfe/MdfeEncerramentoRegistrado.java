package com.fiscaladapter.mdfe;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Registra que um MDF-e foi encerrado (FIS-54) - a SEFAZ nao devolve esse
 * status na consulta de situacao usada por este adapter (`ConsultaProtocoloResponse`
 * so tem cStat/protocolo da autorizacao), entao o proprio encerramento
 * (`MdfeConsultaController.encerrar`) precisa gravar esse fato para: (1)
 * bloquear cancelamento apos o encerramento (AC2) e (2) informar a consulta
 * (AC3). Nao ha rotina de exclusao (mesmo espirito do arquivamento legal,
 * FIS-26/34).
 */
@Entity
@Table(
        name = "mdfe_encerramento",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chave_acesso"})
)
public class MdfeEncerramentoRegistrado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_acesso", nullable = false, length = 44)
    private String chaveAcesso;

    @Column(name = "codigo_municipio_encerramento", nullable = false, length = 7)
    private String codigoMunicipioEncerramento;

    @Column(name = "data_encerramento", nullable = false)
    private LocalDate dataEncerramento;

    @Column(name = "encerrado_em", nullable = false)
    private Instant encerradoEm;

    protected MdfeEncerramentoRegistrado() {
        // JPA
    }

    public MdfeEncerramentoRegistrado(String chaveAcesso, String codigoMunicipioEncerramento,
                                       LocalDate dataEncerramento, Instant encerradoEm) {
        this.chaveAcesso = chaveAcesso;
        this.codigoMunicipioEncerramento = codigoMunicipioEncerramento;
        this.dataEncerramento = dataEncerramento;
        this.encerradoEm = encerradoEm;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    public String getCodigoMunicipioEncerramento() {
        return codigoMunicipioEncerramento;
    }

    public LocalDate getDataEncerramento() {
        return dataEncerramento;
    }

    public Instant getEncerradoEm() {
        return encerradoEm;
    }
}
