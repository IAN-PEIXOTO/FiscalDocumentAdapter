package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** vProd de cada item deve ser igual a qCom * vUnCom (a SEFAZ rejeita a nota se nao bater). */
@Component
public class RegraTotalItemConsistente implements RegraNegocio {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        List<ViolacaoRegra> violacoes = new ArrayList<>();
        for (ItemNota item : nfe.itens()) {
            BigDecimal esperado = item.quantidade().multiply(item.valorUnitario());
            BigDecimal diferenca = esperado.subtract(item.valorTotal()).abs();
            if (diferenca.compareTo(TOLERANCIA) > 0) {
                violacoes.add(new ViolacaoRegra("RVN-001",
                        "Item " + item.numero() + ": vProd (" + item.valorTotal()
                                + ") nao corresponde a qCom * vUnCom (" + esperado + ")"));
            }
        }
        return violacoes;
    }
}
