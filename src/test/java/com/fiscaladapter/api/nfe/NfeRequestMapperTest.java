package com.fiscaladapter.api.nfe;

import com.fiscaladapter.documento.nfe.ImpostoItem;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfeRequestMapperTest {

    private final NfeRequestMapper mapper = new NfeRequestMapper();

    @Test
    void deveMapearGrupoIcms00ComValores() {
        Icms00Request icms00 = new Icms00Request("0", "00", 3, BigDecimal.valueOf(100.00), BigDecimal.valueOf(18.00), BigDecimal.valueOf(18.00));

        ImpostoItem imposto = mapearImposto(new IcmsRequest(icms00, null, null));

        assertThat(imposto.grupoIcms()).isEqualTo("ICMS00");
        assertThat(imposto.codigoIcms()).isEqualTo("00");
        assertThat(imposto.valorIcms()).isEqualByComparingTo("18.00");
    }

    @Test
    void deveMapearGrupoIcms40ZerandoValores() {
        Icms40Request icms40 = new Icms40Request("0", "40");

        ImpostoItem imposto = mapearImposto(new IcmsRequest(null, icms40, null));

        assertThat(imposto.grupoIcms()).isEqualTo("ICMS40");
        assertThat(imposto.codigoIcms()).isEqualTo("40");
        assertThat(imposto.valorIcms()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(imposto.baseCalculoIcms()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deveMapearGrupoIcmsSn102ZerandoValores() {
        IcmsSN102Request icmsSn102 = new IcmsSN102Request("0", "102");

        ImpostoItem imposto = mapearImposto(new IcmsRequest(null, null, icmsSn102));

        assertThat(imposto.grupoIcms()).isEqualTo("ICMSSN102");
        assertThat(imposto.codigoIcms()).isEqualTo("102");
        assertThat(imposto.valorIcms()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void deveRejeitarQuandoNenhumGrupoIcmsInformado() {
        assertThatThrownBy(() -> new IcmsRequest(null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarQuandoMaisDeUmGrupoIcmsInformado() {
        Icms00Request icms00 = new Icms00Request("0", "00", 3, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        Icms40Request icms40 = new Icms40Request("0", "40");

        assertThatThrownBy(() -> new IcmsRequest(icms00, icms40, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ImpostoItem mapearImposto(IcmsRequest icms) {
        NfePedidoEmissaoRequest pedido = pedidoCom(icms);
        NotaFiscalEletronica nfe = mapper.paraDominio(pedido);
        return nfe.itens().get(0).imposto();
    }

    private NfePedidoEmissaoRequest pedidoCom(IcmsRequest icms) {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rua Teste", "100", null, "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitRequest emit = new EmitRequest("12345678000199", null, "EMPRESA TESTE LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "1");

        EnderecoNfeRequest enderecoDestinatario = new EnderecoNfeRequest("Av. Cliente", "200", null, "Jardins", "3550308", "Sao Paulo", "SP", "02000000", "1058", "Brasil", null);
        DestRequest dest = new DestRequest(null, "98765432100", null, "CLIENTE TESTE", enderecoDestinatario, 9, null, null, null, "cliente@teste.com");

        PisAliqRequest pisAliq = new PisAliqRequest("01", BigDecimal.valueOf(100.00), BigDecimal.valueOf(1.65), BigDecimal.valueOf(1.65));
        CofinsAliqRequest cofinsAliq = new CofinsAliqRequest("01", BigDecimal.valueOf(100.00), BigDecimal.valueOf(7.60), BigDecimal.valueOf(7.60));
        ImpostoRequest imposto = new ImpostoRequest(icms, null, new PisRequest(pisAliq), new CofinsRequest(cofinsAliq));

        ProdRequest prod = new ProdRequest("PROD001", "SEM GTIN", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE, BigDecimal.valueOf(100.00), BigDecimal.valueOf(100.00),
                "SEM GTIN", "UN", BigDecimal.ONE, BigDecimal.valueOf(100.00), 1);

        DetRequest det = new DetRequest(1, prod, imposto);

        IdeRequest ide = new IdeRequest(35, "VENDA DE MERCADORIA", 1, 42L, LocalDate.of(2026, 3, 15),
                1, 1, "3550308", 1, 1, 2, 1, 1, 9, 0, "1.0.0");

        TranspRequest transp = new TranspRequest(9);
        PagRequest pag = new PagRequest(List.of(new DetPagRequest("01", BigDecimal.valueOf(100.00))));

        InfNfeRequest infNFe = new InfNfeRequest(ide, emit, dest, List.of(det), transp, pag);

        return new NfePedidoEmissaoRequest("homologacao", "teste-001", infNFe);
    }
}
