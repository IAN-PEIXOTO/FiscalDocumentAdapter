package com.fiscaladapter.documento.cte;

import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.TipoAmbiente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CteTestFixture {

    private CteTestFixture() {
    }

    public static Cte cteDeExemplo() {
        Endereco enderecoEmitente = new Endereco("Rod. BR-101", "km 10", "Distrito Industrial", "3550308", "Sao Paulo", "SP", "01000000", "1130000000");
        Emitente emitente = new Emitente("12.345.678/0001-99", "TRANSPORTADORA TESTE LTDA", "TT TRANSPORTES", "111222333", "3", enderecoEmitente);

        Endereco enderecoRemetente = new Endereco("Rua Origem", "10", "Centro", "3550308", "Sao Paulo", "SP", "01000000", null);
        ParticipanteCte remetente = new ParticipanteCte("11.222.333/0001-44", "111222333", "REMETENTE TESTE LTDA", enderecoRemetente, "remetente@teste.com");

        Endereco enderecoDestinatario = new Endereco("Rua Destino", "20", "Centro", "3304557", "Rio de Janeiro", "RJ", "20000000", null);
        ParticipanteCte destinatario = new ParticipanteCte("55.666.777/0001-88", "556667770", "DESTINATARIO TESTE LTDA", enderecoDestinatario, "destinatario@teste.com");

        IdentificacaoCte ide = new IdentificacaoCte("SP", "6353", "PRESTACAO DE SERVICO DE TRANSPORTE", 1, 42,
                LocalDate.of(2026, 3, 15), TipoAmbiente.HOMOLOGACAO,
                "3550308", "Sao Paulo", "SP",
                "3304557", "Rio de Janeiro", "RJ");

        ImpostoCte imposto = new ImpostoCte(BigDecimal.valueOf(1000.00).setScale(2), BigDecimal.valueOf(12.00).setScale(2), BigDecimal.valueOf(120.00).setScale(2));
        InformacaoCarga infoCarga = new InformacaoCarga(BigDecimal.valueOf(5000.00).setScale(2), "MERCADORIAS DIVERSAS", BigDecimal.valueOf(1500.0000).setScale(4));

        return new Cte(ide, emitente, remetente, destinatario, TipoTomadorServico.REMETENTE,
                BigDecimal.valueOf(1000.00).setScale(2), BigDecimal.valueOf(1000.00).setScale(2),
                imposto, infoCarga,
                List.of(new NotaFiscalTransportada("35260112345678000199550010000000421000000019")),
                "12345678");
    }
}
