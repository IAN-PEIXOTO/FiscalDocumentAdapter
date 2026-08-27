package com.fiscaladapter.certificado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificadoEmissorRepository extends JpaRepository<CertificadoEmissor, Long> {

    Optional<CertificadoEmissor> findByCnpj(String cnpj);

    void deleteByCnpj(String cnpj);
}
