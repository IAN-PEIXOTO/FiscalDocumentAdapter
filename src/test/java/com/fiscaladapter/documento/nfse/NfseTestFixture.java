package com.fiscaladapter.documento.nfse;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class NfseTestFixture {

    private NfseTestFixture() {
    }

    public static Nfse nfseDeExemplo() {
        InfRps infRps = new InfRps(
                new IdentificacaoRps(42, "1", TipoRps.RPS),
                LocalDate.of(2026, 3, 15),
                1);

        ValoresServicoNfse valores = new ValoresServicoNfse(
                BigDecimal.valueOf(1000.00).setScale(2),
                null,
                BigDecimal.valueOf(50.00).setScale(2),
                BigDecimal.valueOf(5.0000).setScale(4));

        DadosServicoNfse servico = new DadosServicoNfse(
                valores, false, "0107", "DESENVOLVIMENTO DE SOFTWARE SOB ENCOMENDA", "3550308", 1);

        PrestadorServicoNfse prestador = new PrestadorServicoNfse("12.345.678/0001-99", "123456");

        EnderecoNfse enderecoTomador = new EnderecoNfse("Av. Cliente", "200", null, "Jardins", "3550308", "SP", "02000000");
        TomadorServicoNfse tomador = new TomadorServicoNfse("987.654.321-00", null, "CLIENTE TESTE",
                enderecoTomador, "1140000000", "cliente@teste.com");

        return new Nfse(infRps, LocalDate.of(2026, 3, 1), servico, prestador, tomador, false, false);
    }
}
