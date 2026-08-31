package com.fiscaladapter.documento.cte.dacte;

import com.fiscaladapter.documento.cte.CteTestFixture;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DacteGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final DacteGenerator generator = new DacteGenerator();

    @Test
    void deveGerarPdfValidoSemAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "57", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(CteTestFixture.cteDeExemplo(), chave, DadosImpressaoDacte.semAutorizacao());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfValidoComAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "57", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(CteTestFixture.cteDeExemplo(), chave,
                new DadosImpressaoDacte("135260000000001", OffsetDateTime.now()));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
