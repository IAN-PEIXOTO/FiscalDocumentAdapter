package com.fiscaladapter.seguranca;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Vincula um CNPJ emissor ao cliente de API (tenant) que o registrou primeiro
 * (FIS-10). Garante isolamento entre tenants na mesma instancia: um client_id
 * so pode emitir, consultar ou cancelar documentos do CNPJ que ele mesmo
 * cadastrou - nao de qualquer CNPJ registrado por outro cliente, mesmo que
 * ele conheça/adivinhe o numero.
 */
@Entity
@Table(name = "emissor_autorizado")
public class EmissorAutorizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected EmissorAutorizado() {
        // JPA
    }

    public EmissorAutorizado(String cnpj, String clientId) {
        this.cnpj = cnpj;
        this.clientId = clientId;
        this.criadoEm = Instant.now();
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getClientId() {
        return clientId;
    }
}
