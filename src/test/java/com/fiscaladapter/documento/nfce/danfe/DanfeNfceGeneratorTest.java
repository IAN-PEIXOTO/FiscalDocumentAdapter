package com.fiscaladapter.documento.nfce.danfe;

import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DanfeNfceGeneratorTest {

    private static final String URL_CONSULTA = "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta";

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final DanfeNfceGenerator generator = new DanfeNfceGenerator();

    @Test
    void deveGerarPdfValidoSemAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "65", 1, 42, 1, "10000001");
        String conteudoQrCode = URL_CONSULTA + "?p=" + chave + "|3|1";

        byte[] pdf = generator.gerar(
                NotaFiscalEletronicaTestFixture.notaDeExemplo(),
                chave,
                new DadosImpressaoDanfeNfce(false, null, null, conteudoQrCode, URL_CONSULTA)
        );

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfValidoComAutorizacao() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "65", 1, 42, 1, "10000001");
        String conteudoQrCode = URL_CONSULTA + "?p=" + chave + "|3|1";

        byte[] pdf = generator.gerar(
                NotaFiscalEletronicaTestFixture.notaDeExemplo(),
                chave,
                new DadosImpressaoDanfeNfce(false, "135260000000001", OffsetDateTime.now(), conteudoQrCode, URL_CONSULTA)
        );

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfComIndicacaoDeContingencia() {
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "65", 1, 42, 1, "10000001");
        String conteudoQrCode = URL_CONSULTA + "?p=" + chave + "|3|1";

        byte[] pdf = generator.gerar(
                NotaFiscalEletronicaTestFixture.notaDeExemplo(),
                chave,
                new DadosImpressaoDanfeNfce(true, null, null, conteudoQrCode, URL_CONSULTA)
        );

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
