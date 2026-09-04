package com.fiscaladapter.api.nfe.assincrono;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Reivindica atomicamente um job PENDENTE (FIS-74): um UPDATE condicional que so afeta a
     * linha se o status ainda for PENDENTE no momento exato da escrita, evitando a corrida em que
     * duas instancias da aplicacao (ou dois ciclos de poll concorrentes) leem o mesmo job como
     * elegivel e ambas comecam a processa-lo. Retorna quantas linhas foram atualizadas: 1 se esta
     * chamada venceu a corrida, 0 se outra instancia ja reivindicou o job primeiro.
     */
    @Modifying
    @Query("update EmissaoAssincrona e set e.status = com.fiscaladapter.api.nfe.assincrono.StatusEmissaoAssincrona.PROCESSANDO, "
            + "e.atualizadoEm = :agora where e.id = :id and e.status = com.fiscaladapter.api.nfe.assincrono.StatusEmissaoAssincrona.PENDENTE")
    int reivindicarSePendente(@Param("id") Long id, @Param("agora") Instant agora);
}
