package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.DetalhePagamento;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** A soma dos pagamentos (vPag) deve ser igual ao valor total da nota (vNF). */
@Component
public class RegraSomaPagamentosIgualTotal implements RegraNegocio {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        BigDecimal somaPagamentos = nfe.pagamentos().stream()
                .map(DetalhePagamento::valor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal diferenca = somaPagamentos.subtract(nfe.valorTotalNota()).abs();
        if (diferenca.compareTo(TOLERANCIA) > 0) {
            return List.of(new ViolacaoRegra("RVN-004",
                    "Soma dos pagamentos (" + somaPagamentos + ") difere do valor total da nota (" + nfe.valorTotalNota() + ")"));
        }
        return List.of();
    }
}
