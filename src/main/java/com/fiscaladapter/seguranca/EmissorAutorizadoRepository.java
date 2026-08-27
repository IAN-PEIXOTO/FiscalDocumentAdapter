package com.fiscaladapter.seguranca;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmissorAutorizadoRepository extends JpaRepository<EmissorAutorizado, Long> {

    Optional<EmissorAutorizado> findByCnpj(String cnpj);
}
