package com.fiscaladapter.api.nfe.assincrono;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface EmissaoAssincronaRepository extends JpaRepository<EmissaoAssincrona, Long> {

    Optional<EmissaoAssincrona> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    Optional<EmissaoAssincrona> findByIdAndClientId(Long id, String clientId);

    @Query("select e from EmissaoAssincrona e where e.status = :status "
            + "and (e.proximaTentativaEm is null or e.proximaTentativaEm <= :agora) "
            + "order by e.criadoEm asc")
    List<EmissaoAssincrona> buscarElegiveis(@Param("status") StatusEmissaoAssincrona status,
                                             @Param("agora") Instant agora, Pageable pageable);
}
