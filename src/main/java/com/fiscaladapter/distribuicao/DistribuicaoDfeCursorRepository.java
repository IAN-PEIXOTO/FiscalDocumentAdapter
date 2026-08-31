package com.fiscaladapter.distribuicao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DistribuicaoDfeCursorRepository extends JpaRepository<DistribuicaoDfeCursor, Long> {
    Optional<DistribuicaoDfeCursor> findByCnpj(String cnpj);
}
