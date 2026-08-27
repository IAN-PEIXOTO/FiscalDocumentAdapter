package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.TipoAmbiente;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MdfeTestFixture {

    private MdfeTestFixture() {
    }

    public static Mdfe mdfeDeExemplo() {
        Endereco enderecoEmitente = new Endereco("Rod. BR-101", "km 10", "Distrito Industrial", "3550308", "Sao Paulo", "SP", "01000000", "1130000000");
        EmitenteMdfe emitente = new EmitenteMdfe("12.345.678/0001-99", "TRANSPORTADORA TESTE LTDA", "TT TRANSPORTES", "111222333", enderecoEmitente);

        IdentificacaoMdfe ide = new IdentificacaoMdfe("SP", 1, 42, LocalDate.of(2026, 3, 15), TipoAmbiente.HOMOLOGACAO,
                "SP", "RJ", "3550308", "Sao Paulo");

        VeiculoTracao veiculo = new VeiculoTracao("ABC1D23", BigDecimal.valueOf(8000), "03", "02", "SP");
        List<Condutor> condutores = List.of(new Condutor("JOAO DA SILVA", "123.456.789-00"));

        return new Mdfe(ide, emitente, "12345678", veiculo, condutores,
                "3304557", "Rio de Janeiro",
                List.of("35260112345678000199570010000000421000000012"),
                List.of("35260112345678000199550010000000421000000019"),
                BigDecimal.valueOf(5000.00).setScale(2), BigDecimal.valueOf(1500.0000).setScale(4));
    }
}
