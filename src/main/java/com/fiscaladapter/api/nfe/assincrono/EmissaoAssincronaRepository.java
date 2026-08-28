package com.fiscaladapter.api.nfe.assincrono;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface EmissaoAssincronaRepository extends JpaRepository<EmissaoAssincrona, Long> {

    Optional<EmissaoAssincrona> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    Optional<EmissaoAssincrona> findByIdAndClientId(Long id, String clientId);

    List<EmissaoAssincrona> findTop5ByStatusOrderByCriadoEmAsc(StatusEmissaoAssincrona status);
}
