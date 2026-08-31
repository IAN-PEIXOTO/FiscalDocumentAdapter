package com.fiscaladapter.api.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RequisicaoIdempotenteRepository extends JpaRepository<RequisicaoIdempotente, Long> {

    Optional<RequisicaoIdempotente> findByClientIdAndTipoOperacaoAndChave(String clientId, String tipoOperacao, String chave);

    void deleteByClientIdAndTipoOperacaoAndChave(String clientId, String tipoOperacao, String chave);
}
