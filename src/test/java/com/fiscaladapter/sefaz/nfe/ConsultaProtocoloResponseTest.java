package com.fiscaladapter.sefaz.nfe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FIS-88: mesmos cStat de sucesso de {@link AutorizacaoResponseTest} - antes desta correcao, so
 * cStat 100 era reconhecido aqui, entao uma consulta de protocolo (ou a recuperacao de duplicidade
 * em EmissaoNfeOrquestrador) reportava como rejeitado um documento autorizado com cStat 150.
 */
class ConsultaProtocoloResponseTest {

    @Test
    void cStat100DeveSerAutorizada() {
        ConsultaProtocoloResponse resposta = ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e",
                "135260000000001", "2026-03-15T10:00:00-03:00");

        assertThat(resposta.autorizada()).isTrue();
    }

    @Test
    void cStat150AutorizadoForaDePrazoDeveSerAutorizada() {
        ConsultaProtocoloResponse resposta = ConsultaProtocoloResponse.de("150",
                "Autorizado o uso da NF-e, autorizado fora de prazo", "135260000000002", "2026-03-15T10:00:00-03:00");

        assertThat(resposta.autorizada()).isTrue();
    }

    @Test
    void cStatDeRejeicaoNaoDeveSerAutorizada() {
        ConsultaProtocoloResponse resposta = ConsultaProtocoloResponse.de("225", "Falha no schema XML", null, null);

        assertThat(resposta.autorizada()).isFalse();
    }

    /** FIS-106: usados por EmissaoNfeOrquestrador.recuperarProtocoloSeDuplicidade para reconhecer uma denegacao real por tras de um 204. */
    @Test
    void cStatDeDenegacaoDeveSerDenegadaENaoAutorizada() {
        ConsultaProtocoloResponse resposta = ConsultaProtocoloResponse.de("301",
                "Uso Denegado: Irregularidade fiscal do emitente", null, null);

        assertThat(resposta.denegada()).isTrue();
        assertThat(resposta.autorizada()).isFalse();
    }

    @Test
    void cStatDeRejeicaoNaoDeveSerDenegada() {
        ConsultaProtocoloResponse resposta = ConsultaProtocoloResponse.de("225", "Falha no schema XML", null, null);

        assertThat(resposta.denegada()).isFalse();
    }
}
