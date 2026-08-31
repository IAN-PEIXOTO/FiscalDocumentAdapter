package com.fiscaladapter.mdfe;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface MdfeEncerramentoRegistradoRepository extends JpaRepository<MdfeEncerramentoRegistrado, Long> {

    Optional<MdfeEncerramentoRegistrado> findByChaveAcesso(String chaveAcesso);
}
