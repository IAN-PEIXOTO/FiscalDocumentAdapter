package com.fiscaladapter.seguranca;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteApiRepository extends JpaRepository<ClienteApi, Long> {

    Optional<ClienteApi> findByClientId(String clientId);
}
