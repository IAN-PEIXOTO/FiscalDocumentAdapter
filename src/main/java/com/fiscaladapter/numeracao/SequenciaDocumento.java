package com.fiscaladapter.numeracao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Um numero de documento fiscal ja reservado/utilizado para um dado
 * emissor/UF/serie/tipo de documento (FIS-23). Uma linha por numero, nao um
 * contador - a constraint unica em (cnpj_emissor, uf, serie, tipo_documento,
 * numero) e o proprio mecanismo de deteccao atomica de duplicidade sob
 * concorrencia: duas requisicoes tentando reservar o mesmo numero disputam a
 * mesma linha no banco, so uma consegue inserir.
 */
@Entity
@Table(
        name = "sequencia_documento",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cnpj_emissor", "uf", "serie", "tipo_documento", "numero"})
)
public class SequenciaDocumento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cnpj_emissor", nullable = false, length = 14)
    private String cnpjEmissor;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(nullable = false)
    private int serie;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 10)
    private TipoDocumentoFiscal tipoDocumento;

    @Column(nullable = false)
    private long numero;

    protected SequenciaDocumento() {
        // JPA
    }

    SequenciaDocumento(String cnpjEmissor, String uf, int serie, TipoDocumentoFiscal tipoDocumento, long numero) {
        this.cnpjEmissor = cnpjEmissor;
        this.uf = uf;
        this.serie = serie;
        this.tipoDocumento = tipoDocumento;
        this.numero = numero;
    }

    public Long getId() {
        return id;
    }

    public String getCnpjEmissor() {
        return cnpjEmissor;
    }

    public String getUf() {
        return uf;
    }

    public int getSerie() {
        return serie;
    }

    public TipoDocumentoFiscal getTipoDocumento() {
        return tipoDocumento;
    }

    public long getNumero() {
        return numero;
    }
}
