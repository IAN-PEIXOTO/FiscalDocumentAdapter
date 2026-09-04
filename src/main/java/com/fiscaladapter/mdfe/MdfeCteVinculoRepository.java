package com.fiscaladapter.mdfe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface MdfeCteVinculoRepository extends JpaRepository<MdfeCteVinculo, Long> {

    Optional<MdfeCteVinculo> findByChaveCte(String chaveCte);
}
