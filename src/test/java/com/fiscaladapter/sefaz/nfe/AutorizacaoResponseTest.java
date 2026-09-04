package com.fiscaladapter.sefaz.nfe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Prova os cStat de sucesso reconhecidos na autorizacao (FIS-71) - compartilhado por NF-e/CT-e/MDF-e. */
class AutorizacaoResponseTest {

    @Test
    void cStat100DeveSerAutorizada() {
        AutorizacaoResponse resposta = AutorizacaoResponse.de("100", "Autorizado o uso da NF-e", "135260000000001", "2026-03-15T10:00:00-03:00");

        assertThat(resposta.autorizada()).isTrue();
    }

    @Test
    void cStat150AutorizadoForaDePrazoDeveSerAutorizada() {
        AutorizacaoResponse resposta = AutorizacaoResponse.de("150", "Autorizado o uso da NF-e, autorizado fora de prazo",
                "135260000000002", "2026-03-15T10:00:00-03:00");

        assertThat(resposta.autorizada()).isTrue();
    }

    @Test
    void cStatDeRejeicaoNaoDeveSerAutorizada() {
        AutorizacaoResponse resposta = AutorizacaoResponse.de("225", "Falha no schema XML", null, null);

        assertThat(resposta.autorizada()).isFalse();
    }
}
