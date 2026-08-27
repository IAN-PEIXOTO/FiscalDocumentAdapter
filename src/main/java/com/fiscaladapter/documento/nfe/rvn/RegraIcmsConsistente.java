package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.ImpostoItem;
import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** vICMS de cada item deve ser igual a vBC * pICMS / 100 (tolerando arredondamento de centavos). */
@Component
public class RegraIcmsConsistente implements RegraNegocio {

    private static final BigDecimal TOLERANCIA = new BigDecimal("0.02");
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        List<ViolacaoRegra> violacoes = new ArrayList<>();
        for (ItemNota item : nfe.itens()) {
            ImpostoItem imposto = item.imposto();
            BigDecimal esperado = imposto.baseCalculoIcms()
                    .multiply(imposto.aliquotaIcms())
                    .divide(CEM, 2, RoundingMode.HALF_UP);
            BigDecimal diferenca = esperado.subtract(imposto.valorIcms()).abs();
            if (diferenca.compareTo(TOLERANCIA) > 0) {
                violacoes.add(new ViolacaoRegra("RVN-002",
                        "Item " + item.numero() + ": vICMS (" + imposto.valorIcms()
                                + ") nao corresponde a vBC * pICMS / 100 (" + esperado + ")"));
            }
        }
        return violacoes;
    }
}
