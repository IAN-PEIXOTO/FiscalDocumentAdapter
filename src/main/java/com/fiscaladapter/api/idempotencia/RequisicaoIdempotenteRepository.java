package com.fiscaladapter.api.idempotencia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface RequisicaoIdempotenteRepository extends JpaRepository<RequisicaoIdempotente, Long> {

    Optional<RequisicaoIdempotente> findByClientIdAndTipoOperacaoAndChave(String clientId, String tipoOperacao, String chave);

    void deleteByClientIdAndTipoOperacaoAndChave(String clientId, String tipoOperacao, String chave);

    /**
     * Expurgo periodico (FIS-81): cada Idempotency-Key normalmente e usada uma unica vez, entao a
     * remocao preguicosa em obterOuCriarPlaceholder (so quando a MESMA chave e reenviada apos
     * expirar) nunca alcanca a maioria das linhas - a tabela cresceria proporcionalmente ao
     * volume historico total, nao ao volume ativo, sem este expurgo.
     */
    @Modifying
    @Query("delete from RequisicaoIdempotente r where r.expiraEm < :agora")
    int expurgarExpiradasAntesDe(@Param("agora") Instant agora);
}
