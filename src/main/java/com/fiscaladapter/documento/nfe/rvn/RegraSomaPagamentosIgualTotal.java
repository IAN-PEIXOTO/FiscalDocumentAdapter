package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.DetalhePagamento;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * A soma dos pagamentos (vPag), descontado o troco (vTroco), deve ser igual ao valor total da
 * nota (vNF) - permite pagamento em dinheiro maior que o total com troco (FIS-72), caso comum em
 * NFC-e de PDV que antes era bloqueado por esta regra (vPag > vNF sem nenhum campo para explicar
 * a diferenca).
 */
@Component
public class RegraSomaPagamentosIgualTotal implements RegraNegocio {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        BigDecimal somaPagamentos = nfe.pagamentos().stream()
                .map(DetalhePagamento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal somaLiquida = somaPagamentos.subtract(nfe.valorTroco());

        BigDecimal diferenca = somaLiquida.subtract(nfe.valorTotalNota()).abs();
        if (diferenca.compareTo(TOLERANCIA) > 0) {
            return List.of(new ViolacaoRegra("RVN-004",
                    "Soma dos pagamentos (" + somaPagamentos + "), descontado o troco (" + nfe.valorTroco()
                            + "), difere do valor total da nota (" + nfe.valorTotalNota() + ")"));
        }
        return List.of();
    }
}
