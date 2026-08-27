package com.fiscaladapter.documento.nfe;

import com.fiscaladapter.documento.TipoDocumentoFiscal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Fixture de NotaFiscalEletronica reutilizavel entre testes de outros pacotes (assinatura, etc.). */
public final class NotaFiscalEletronicaTestFixture {

    private NotaFiscalEletronicaTestFixture() {
    }

    public static NotaFiscalEletronica notaDeExemplo() {
        return notaComImposto(impostoIcms00());
    }

    public static NotaFiscalEletronica notaComImposto(ImpostoItem imposto) {
        Endereco enderecoEmitente = new Endereco("Rua Teste", "100", "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1130000000");
        Emitente emitente = new Emitente("12.345.678/0001-99", "EMPRESA TESTE LTDA", "TESTE", "111222333", "1", enderecoEmitente);

        Endereco enderecoDestinatario = new Endereco("Av. Cliente", "200", "Jardins", "3550308", "Sao Paulo", "SP", "02000000", null);
        Destinatario destinatario = new Destinatario("987.654.321-00", "CLIENTE TESTE", "9", null, "cliente@teste.com", enderecoDestinatario);

        ItemNota item = new ItemNota(1, "PROD001", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE.setScale(4), BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(100.00).setScale(2), imposto);

        IdentificacaoNfe ide = new IdentificacaoNfe("SP", "VENDA DE MERCADORIA", 1, 42,
                LocalDate.of(2026, 3, 15), TipoAmbiente.HOMOLOGACAO, 1, true, "3550308", TipoDocumentoFiscal.NFE);

        DetalhePagamento pagamento = new DetalhePagamento("01", BigDecimal.valueOf(100.00).setScale(2));

        return new NotaFiscalEletronica(ide, emitente, destinatario, List.of(item), List.of(pagamento));
    }

    /** NFC-e (modelo 65) para consumidor nao identificado - sem destinatario (FIS-17). */
    public static NotaFiscalEletronica notaNfceSemDestinatario() {
        Endereco enderecoEmitente = new Endereco("Rua Teste", "100", "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1130000000");
        Emitente emitente = new Emitente("12.345.678/0001-99", "EMPRESA TESTE LTDA", "TESTE", "111222333", "1", enderecoEmitente);

        ItemNota item = new ItemNota(1, "PROD001", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE.setScale(4), BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(100.00).setScale(2),
                impostoIcms00());

        IdentificacaoNfe ide = new IdentificacaoNfe("SP", "VENDA DE MERCADORIA", 1, 42,
                LocalDate.of(2026, 3, 15), TipoAmbiente.HOMOLOGACAO, 1, true, "3550308", TipoDocumentoFiscal.NFCE);

        DetalhePagamento pagamento = new DetalhePagamento("01", BigDecimal.valueOf(100.00).setScale(2));

        return new NotaFiscalEletronica(ide, emitente, null, List.of(item), List.of(pagamento));
    }

    public static ImpostoItem impostoIcms00() {
        return new ImpostoItem("ICMS00", "0", "00",
                BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(18.00).setScale(2), BigDecimal.valueOf(18.00).setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.valueOf(1.65).setScale(2), BigDecimal.valueOf(7.60).setScale(2));
    }

    public static ImpostoItem impostoIcms40Isenta() {
        return new ImpostoItem("ICMS40", "0", "40",
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.valueOf(1.65).setScale(2), BigDecimal.valueOf(7.60).setScale(2));
    }

    public static ImpostoItem impostoIcmsSN102() {
        return new ImpostoItem("ICMSSN102", "0", "102",
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.valueOf(1.65).setScale(2), BigDecimal.valueOf(7.60).setScale(2));
    }
}
