package com.fiscaladapter.certificado;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Certificado A1 de um emissor, persistido para nao depender de reenvio do
 * arquivo .p12 a cada emissao de NFe (FIS-2). O arquivo e a senha ficam
 * criptografados em repouso (AES-256-GCM, ver CriptografiaEmRepousoService) -
 * so existem em texto puro/decifrados durante o processamento de uma
 * requisicao, nunca em disco.
 */
@Entity
@Table(name = "certificado_emissor")
public class CertificadoEmissor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(nullable = false, length = 200)
    private String alias;

    @Column(name = "subject_dn", nullable = false, length = 500)
    private String subjectDn;

    @Lob
    @Column(name = "p12_criptografado", nullable = false)
    private String p12Criptografado;

    @Lob
    @Column(name = "senha_criptografada", nullable = false)
    private String senhaCriptografada;

    @Column(name = "valido_de", nullable = false)
    private Instant validoDe;

    @Column(name = "valido_ate", nullable = false)
    private Instant validoAte;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    private long version;

    protected CertificadoEmissor() {
        // JPA
    }

    public CertificadoEmissor(String cnpj, CertificadoInfo info, String p12Criptografado, String senhaCriptografada) {
        this.cnpj = cnpj;
        this.p12Criptografado = p12Criptografado;
        this.senhaCriptografada = senhaCriptografada;
        this.criadoEm = Instant.now();
        atualizarDadosDoCertificado(info, p12Criptografado, senhaCriptografada);
    }

    /** Substitui o certificado registrado (renovacao/troca do A1) mantendo o mesmo registro. */
    public void atualizarDadosDoCertificado(CertificadoInfo info, String p12Criptografado, String senhaCriptografada) {
        this.alias = info.alias();
        this.subjectDn = info.subjectDn();
        this.validoDe = info.validoDe();
        this.validoAte = info.validoAte();
        this.p12Criptografado = p12Criptografado;
        this.senhaCriptografada = senhaCriptografada;
        this.atualizadoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getSubjectDn() {
        return subjectDn;
    }

    public String getP12Criptografado() {
        return p12Criptografado;
    }

    public String getSenhaCriptografada() {
        return senhaCriptografada;
    }

    public Instant getValidoAte() {
        return validoAte;
    }
}
