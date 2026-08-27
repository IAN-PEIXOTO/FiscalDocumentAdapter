package com.fiscaladapter.documento.nfe.danfe;

import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DanfeGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final DanfeGenerator generator = new DanfeGenerator();

    @Test
    void deveGerarPdfValidoEmRetratoSemAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "55", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(
                NotaFiscalEletronicaTestFixture.notaDeExemplo(),
                chave,
                DadosImpressaoDanfe.semAutorizacao(OrientacaoDanfe.RETRATO, false)
        );

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfValidoEmPaisagemComAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "55", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(
                NotaFiscalEletronicaTestFixture.notaDeExemplo(),
                chave,
                new DadosImpressaoDanfe(OrientacaoDanfe.PAISAGEM, false, "135260000000001", OffsetDateTime.now())
        );

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfComIndicacaoDeContingencia() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "55", 1, 42, 1, "10000001");

        byte[] pdf = generator.gerar(
                NotaFiscalEletronicaTestFixture.notaDeExemplo(),
                chave,
                DadosImpressaoDanfe.semAutorizacao(OrientacaoDanfe.RETRATO, true)
        );

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
