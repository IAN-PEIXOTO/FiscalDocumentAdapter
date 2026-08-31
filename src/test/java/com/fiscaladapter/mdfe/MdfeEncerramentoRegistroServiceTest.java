package com.fiscaladapter.mdfe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Prova o registro de encerramento do MDF-e (FIS-54) - base para o bloqueio de cancelamento e para os "documentos vinculados" na consulta. */
@SpringBootTest
class MdfeEncerramentoRegistroServiceTest {

    @Autowired
    private MdfeEncerramentoRegistroService service;

    @Test
    void naoDeveEstarEncerradoAntesDoRegistro() {
        assertThat(service.estaEncerrado("35260198765432000199580010000000421000000091")).isFalse();
    }

    @Test
    void deveEstarEncerradoAposORegistro() {
        String chave = "35260198765432000199580010000000421000000092";

        service.registrar(chave, "3304557", LocalDate.of(2026, 3, 20));

        assertThat(service.estaEncerrado(chave)).isTrue();
        assertThat(service.consultar(chave)).isPresent();
        assertThat(service.consultar(chave).get().getCodigoMunicipioEncerramento()).isEqualTo("3304557");
    }

    @Test
    void registrarDuasVezesNaoDeveFalhar() {
        String chave = "35260198765432000199580010000000421000000093";

        service.registrar(chave, "3304557", LocalDate.of(2026, 3, 20));
        service.registrar(chave, "3304557", LocalDate.of(2026, 3, 20));

        assertThat(service.estaEncerrado(chave)).isTrue();
    }
}
