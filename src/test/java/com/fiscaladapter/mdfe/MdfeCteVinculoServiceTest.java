package com.fiscaladapter.mdfe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prova o indice de vinculo CT-e -> MDF-e (FIS-61) - base da consulta "eventos vinculados" do CT-e (FIS-53). */
@SpringBootTest
class MdfeCteVinculoServiceTest {

    @Autowired
    private MdfeCteVinculoService service;

    @Test
    void naoDeveHaverVinculoAntesDoRegistro() {
        assertThat(service.mdfeVinculado("35260198765432000199570010000000421000000071")).isNull();
    }

    @Test
    void deveRetornarMdfeVinculadoAposORegistro() {
        String chaveCte = "35260198765432000199570010000000421000000072";
        String chaveMdfe = "35260198765432000199580010000000421000000072";

        service.registrar(chaveMdfe, List.of(chaveCte));

        assertThat(service.mdfeVinculado(chaveCte)).isEqualTo(chaveMdfe);
    }

    @Test
    void registrarDuasVezesNaoDeveFalhar() {
        String chaveCte = "35260198765432000199570010000000421000000073";
        String chaveMdfe = "35260198765432000199580010000000421000000073";

        service.registrar(chaveMdfe, List.of(chaveCte));
        service.registrar(chaveMdfe, List.of(chaveCte));

        assertThat(service.mdfeVinculado(chaveCte)).isEqualTo(chaveMdfe);
    }

    @Test
    void deveRegistrarVariosChavesCteParaOMesmoMdfe() {
        String chaveCte1 = "35260198765432000199570010000000421000000074";
        String chaveCte2 = "35260198765432000199570010000000421000000075";
        String chaveMdfe = "35260198765432000199580010000000421000000074";

        service.registrar(chaveMdfe, List.of(chaveCte1, chaveCte2));

        assertThat(service.mdfeVinculado(chaveCte1)).isEqualTo(chaveMdfe);
        assertThat(service.mdfeVinculado(chaveCte2)).isEqualTo(chaveMdfe);
    }
}
