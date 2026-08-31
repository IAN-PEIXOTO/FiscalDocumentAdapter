package com.fiscaladapter.retencao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface DocumentoFiscalArquivadoRepository extends JpaRepository<DocumentoFiscalArquivado, Long> {

    Optional<DocumentoFiscalArquivado> findByChaveAcesso(String chaveAcesso);

    List<DocumentoFiscalArquivado> findByCnpjEmissorAndTipoDocumento(String cnpjEmissor, TipoDocumentoFiscal tipoDocumento);
}
