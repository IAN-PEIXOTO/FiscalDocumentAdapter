package com.fiscaladapter.documento.mdfe.damdfe;

import com.fiscaladapter.documento.mdfe.MdfeTestFixture;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DamdfeGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final DamdfeGenerator generator = new DamdfeGenerator();

    @Test
    void deveGerarPdfValidoSemAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "58", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(MdfeTestFixture.mdfeDeExemplo(), chave,
                new DadosImpressaoDamdfe(null, null, false, null, null));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfValidoComAutorizacaoENaoEncerrado() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "58", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(MdfeTestFixture.mdfeDeExemplo(), chave,
                DadosImpressaoDamdfe.deEmissao("135260000000001", OffsetDateTime.now()));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfComIndicacaoDeEncerramento() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "58", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(MdfeTestFixture.mdfeDeExemplo(), chave,
                DadosImpressaoDamdfe.deEncerramento("135260000000001", OffsetDateTime.now(), "3304557", LocalDate.of(2026, 3, 20)));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
