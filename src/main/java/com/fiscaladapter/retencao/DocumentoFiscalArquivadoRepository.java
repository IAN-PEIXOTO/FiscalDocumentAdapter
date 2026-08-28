package com.fiscaladapter.retencao;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface DocumentoFiscalArquivadoRepository extends JpaRepository<DocumentoFiscalArquivado, Long> {

    Optional<DocumentoFiscalArquivado> findByChaveAcesso(String chaveAcesso);
}
