package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.DetalhePagamento;
import com.fiscaladapter.documento.nfe.ImpostoItem;
import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegraNegocioServiceTest {

    private final RegraNegocioService service = new RegraNegocioService(List.of(
            new RegraTotalItemConsistente(),
            new RegraIcmsConsistente(),
            new RegraCfopCompativelComOperacao(),
            new RegraSomaPagamentosIgualTotal()
    ));

    @Test
    void naoDeveLancarExcecaoParaNotaValida() {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        service.validar(nfe);
    }

    @Test
    void deveRejeitarQuandoVProdNaoBateComQuantidadeVezesValorUnitario() {
        NotaFiscalEletronica base = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        ItemNota itemComVprodErrado = trocarValorTotal(base.itens().get(0), BigDecimal.valueOf(999.00));
        NotaFiscalEletronica nfe = comItens(base, List.of(itemComVprodErrado));

        assertThatThrownBy(() -> service.validar(nfe))
                .isInstanceOf(RegraNegocioVioladaException.class)
                .satisfies(e -> assertThat(((RegraNegocioVioladaException) e).getViolacoes())
                        .anyMatch(v -> v.codigo().equals("RVN-001")));
    }

    @Test
    void deveRejeitarQuandoVIcmsNaoBateComBaseVezesAliquota() {
        NotaFiscalEletronica base = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        ItemNota original = base.itens().get(0);
        ImpostoItem impostoErrado = new ImpostoItem(
                original.imposto().origemIcms(), original.imposto().cstIcms(),
                original.imposto().baseCalculoIcms(), original.imposto().aliquotaIcms(),
                BigDecimal.valueOf(50.00), // vICMS errado (deveria ser 18.00)
                original.imposto().valorIpi(), original.imposto().valorPis(), original.imposto().valorCofins());
        ItemNota itemComIcmsErrado = new ItemNota(original.numero(), original.codigoProduto(), original.descricao(),
                original.ncm(), original.cfop(), original.unidadeComercial(), original.quantidade(),
                original.valorUnitario(), original.valorTotal(), impostoErrado);

        NotaFiscalEletronica nfe = comItens(base, List.of(itemComIcmsErrado));

        assertThatThrownBy(() -> service.validar(nfe))
                .isInstanceOf(RegraNegocioVioladaException.class)
                .satisfies(e -> assertThat(((RegraNegocioVioladaException) e).getViolacoes())
                        .anyMatch(v -> v.codigo().equals("RVN-002")));
    }

    @Test
    void deveRejeitarCfopIncompativelComOperacaoInterna() {
        NotaFiscalEletronica base = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        ItemNota original = base.itens().get(0);
        ItemNota itemComCfopInterestadual = new ItemNota(original.numero(), original.codigoProduto(), original.descricao(),
                original.ncm(), "6102", original.unidadeComercial(), original.quantidade(),
                original.valorUnitario(), original.valorTotal(), original.imposto());

        NotaFiscalEletronica nfe = comItens(base, List.of(itemComCfopInterestadual));

        assertThatThrownBy(() -> service.validar(nfe))
                .isInstanceOf(RegraNegocioVioladaException.class)
                .satisfies(e -> assertThat(((RegraNegocioVioladaException) e).getViolacoes())
                        .anyMatch(v -> v.codigo().equals("RVN-003")));
    }

    @Test
    void deveRejeitarQuandoSomaDePagamentosDifereDoTotalDaNota() {
        NotaFiscalEletronica base = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        NotaFiscalEletronica nfe = new NotaFiscalEletronica(base.identificacao(), base.emitente(), base.destinatario(),
                base.itens(), List.of(new DetalhePagamento("01", BigDecimal.valueOf(1.00))));

        assertThatThrownBy(() -> service.validar(nfe))
                .isInstanceOf(RegraNegocioVioladaException.class)
                .satisfies(e -> assertThat(((RegraNegocioVioladaException) e).getViolacoes())
                        .anyMatch(v -> v.codigo().equals("RVN-004")));
    }

    private ItemNota trocarValorTotal(ItemNota original, BigDecimal novoValorTotal) {
        return new ItemNota(original.numero(), original.codigoProduto(), original.descricao(), original.ncm(),
                original.cfop(), original.unidadeComercial(), original.quantidade(), original.valorUnitario(),
                novoValorTotal, original.imposto());
    }

    private NotaFiscalEletronica comItens(NotaFiscalEletronica base, List<ItemNota> itens) {
        return new NotaFiscalEletronica(base.identificacao(), base.emitente(), base.destinatario(), itens, base.pagamentos());
    }
}
