package com.fiscaladapter.numeracao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SequenciaDocumentoRepository extends JpaRepository<SequenciaDocumento, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SequenciaDocumento s "
            + "where s.cnpjEmissor = :cnpjEmissor and s.uf = :uf and s.serie = :serie and s.tipoDocumento = :tipoDocumento")
    Optional<SequenciaDocumento> buscarParaAtualizar(
            @Param("cnpjEmissor") String cnpjEmissor,
            @Param("uf") String uf,
            @Param("serie") int serie,
            @Param("tipoDocumento") TipoDocumentoFiscal tipoDocumento
    );
}
