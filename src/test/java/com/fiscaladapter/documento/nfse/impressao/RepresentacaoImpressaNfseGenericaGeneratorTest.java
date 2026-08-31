package com.fiscaladapter.documento.nfse.impressao;

import com.fiscaladapter.documento.nfse.NfseTestFixture;
import com.fiscaladapter.sefaz.nfse.NfseResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RepresentacaoImpressaNfseGenericaGeneratorTest {

    private final RepresentacaoImpressaNfseGenericaGenerator generator = new RepresentacaoImpressaNfseGenericaGenerator();

    @Test
    void deveGerarPdfValidoQuandoAutorizada() {
        NfseResponse resposta = new NfseResponse("789", "ABC123", null, null);

        byte[] pdf = generator.gerar(NfseTestFixture.nfseDeExemplo(), resposta);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfComAvisoQuandoNaoAutorizada() {
        NfseResponse resposta = new NfseResponse(null, null, "E001", "RPS ja informado anteriormente");

        byte[] pdf = generator.gerar(NfseTestFixture.nfseDeExemplo(), resposta);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveGerarPdfSemTomadorIdentificado() {
        var nfseBase = NfseTestFixture.nfseDeExemplo();
        var nfseSemTomador = new com.fiscaladapter.documento.nfse.Nfse(
                nfseBase.rps(), nfseBase.competencia(), nfseBase.servico(), nfseBase.prestador(), null,
                nfseBase.optanteSimplesNacional(), nfseBase.incentivoFiscal());
        NfseResponse resposta = new NfseResponse("789", "ABC123", null, null);

        byte[] pdf = generator.gerar(nfseSemTomador, resposta);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void deveSuportarQualquerMunicipio() {
        assertThat(generator.suporta("3550308")).isTrue();
        assertThat(generator.suporta("3304557")).isTrue();
    }
}
