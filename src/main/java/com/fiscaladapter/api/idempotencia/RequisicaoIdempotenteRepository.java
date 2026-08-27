package com.fiscaladapter.api.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RequisicaoIdempotenteRepository extends JpaRepository<RequisicaoIdempotente, Long> {

    Optional<RequisicaoIdempotente> findByClientIdAndChave(String clientId, String chave);

    void deleteByClientIdAndChave(String clientId, String chave);
}
