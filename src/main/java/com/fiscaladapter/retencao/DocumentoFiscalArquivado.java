package com.fiscaladapter.retencao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
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
import java.time.LocalDate;

/**
 * Copia do XML assinado de um documento fiscal autorizado (ou liberado via
 * EPEC), guardada para retencao legal (FIS-26/34 - minimo 5 anos exigido
 * pela legislacao fiscal brasileira). Nao ha rotina de exclusao: a ausencia
 * de qualquer delete/expiracao ja satisfaz "no minimo 5 anos" por definicao.
 * xmlAssinadoCriptografado vai cifrado (AES-256-GCM, ver
 * CriptografiaEmRepousoService) por conter dados fiscais sensiveis do
 * emitente/destinatario.
 */
@Entity
@Table(
        name = "documento_fiscal_arquivado",
        uniqueConstraints = @UniqueConstraint(columnNames = {"chave_acesso"})
)
public class DocumentoFiscalArquivado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chave_acesso", nullable = false, length = 44)
    private String chaveAcesso;

    @Column(name = "cnpj_emissor", nullable = false, length = 14)
    private String cnpjEmissor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 10)
    private TipoDocumentoFiscal tipoDocumento;

    @Column(name = "numero_protocolo", length = 30)
    private String numeroProtocolo;

    @Lob
    @Column(name = "xml_assinado_criptografado", nullable = false)
    private String xmlAssinadoCriptografado;

    @Column(name = "data_emissao", nullable = false)
    private LocalDate dataEmissao;

    @Column(name = "arquivado_em", nullable = false)
    private Instant arquivadoEm;

    protected DocumentoFiscalArquivado() {
        // JPA
    }

    public DocumentoFiscalArquivado(String chaveAcesso, String cnpjEmissor, TipoDocumentoFiscal tipoDocumento,
                                     String numeroProtocolo, String xmlAssinadoCriptografado, LocalDate dataEmissao,
                                     Instant arquivadoEm) {
        this.chaveAcesso = chaveAcesso;
        this.cnpjEmissor = cnpjEmissor;
        this.tipoDocumento = tipoDocumento;
        this.numeroProtocolo = numeroProtocolo;
        this.xmlAssinadoCriptografado = xmlAssinadoCriptografado;
        this.dataEmissao = dataEmissao;
        this.arquivadoEm = arquivadoEm;
    }

    public Long getId() {
        return id;
    }

    public String getChaveAcesso() {
        return chaveAcesso;
    }

    public String getCnpjEmissor() {
        return cnpjEmissor;
    }

    public TipoDocumentoFiscal getTipoDocumento() {
        return tipoDocumento;
    }

    public String getNumeroProtocolo() {
        return numeroProtocolo;
    }

    public String getXmlAssinadoCriptografado() {
        return xmlAssinadoCriptografado;
    }

    public LocalDate getDataEmissao() {
        return dataEmissao;
    }

    public Instant getArquivadoEm() {
        return arquivadoEm;
    }
}
