package com.fiscaladapter.sefaz.rejeicao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogoRejeicaoSefazTest {

    @ParameterizedTest
    @CsvSource({
            "108, TRANSITORIO",
            "109, TRANSITORIO",
            "217, TRANSITORIO",
            "656, TRANSITORIO",
            "204, CORRIGIVEL_PELO_CLIENTE",
            "215, CORRIGIVEL_PELO_CLIENTE",
            "225, CORRIGIVEL_PELO_CLIENTE",
            "226, CORRIGIVEL_PELO_CLIENTE",
            "234, CORRIGIVEL_PELO_CLIENTE",
            "235, CORRIGIVEL_PELO_CLIENTE",
            "241, CORRIGIVEL_PELO_CLIENTE",
            "301, CORRIGIVEL_PELO_CLIENTE",
            "302, CORRIGIVEL_PELO_CLIENTE",
            "539, CORRIGIVEL_PELO_CLIENTE",
            "590, CORRIGIVEL_PELO_CLIENTE",
            "110, CORRIGIVEL_PELO_CLIENTE",
    })
    void deveClassificarCodigosCatalogadosNaCategoriaCorreta(String codigo, CategoriaErroSefaz categoriaEsperada) {
        RejeicaoSefaz resultado = CatalogoRejeicaoSefaz.classificar(codigo, "motivo bruto qualquer");

        assertThat(resultado.categoria()).isEqualTo(categoriaEsperada);
        assertThat(resultado.mensagem()).isNotBlank().isNotEqualTo("motivo bruto qualquer");
    }

    @Test
    void deveClassificarCodigoNaoCatalogadoComoDesconhecidaUsandoMotivoBrutoComoMensagem() {
        RejeicaoSefaz resultado = CatalogoRejeicaoSefaz.classificar("777", "Motivo qualquer nunca visto antes");

        assertThat(resultado.categoria()).isEqualTo(CategoriaErroSefaz.DESCONHECIDA);
        assertThat(resultado.mensagem()).isEqualTo("Motivo qualquer nunca visto antes");
    }

    @Test
    void codigoStatusEMotivoBrutoDevemSerSempreOsOriginais() {
        RejeicaoSefaz resultado = CatalogoRejeicaoSefaz.classificar("204", "Duplicidade de NF-e [nRec:123]");

        assertThat(resultado.codigoStatus()).isEqualTo("204");
        assertThat(resultado.motivoBruto()).isEqualTo("Duplicidade de NF-e [nRec:123]");
    }

    @Test
    void erroNaoCatalogadoDeveMarcarFallbackComo999() {
        RejeicaoSefaz resultado = CatalogoRejeicaoSefaz.classificar("999", "Falha interna capturada: NullPointerException");

        assertThat(resultado.categoria()).isEqualTo(CategoriaErroSefaz.DESCONHECIDA);
        assertThat(resultado.mensagem()).contains("nao catalogado");
    }
}
