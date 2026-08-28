package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * O Codigo de Regime Tributario (CRT) do emitente precisa ser compativel com
 * o grupo de ICMS usado nos itens: emitente do Simples Nacional (CRT 1, 2 ou
 * 4) so pode usar os grupos CSOSN (ICMSSN*); emitente do Regime Normal (CRT
 * 3) so pode usar os grupos CST (ICMS00/20/40/60/90/OutraUF). Uma das
 * rejeicoes mais comuns na pratica (CST/CSOSN incompativel com o regime
 * tributario do emitente).
 */
@Component
public class RegraRegimeTributarioCompativelComIcms implements RegraNegocio {

    private static final Set<String> CRT_SIMPLES_NACIONAL = Set.of("1", "2", "4");

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        boolean simplesNacional = CRT_SIMPLES_NACIONAL.contains(nfe.emitente().regimeTributario());

        List<ViolacaoRegra> violacoes = new ArrayList<>();
        for (ItemNota item : nfe.itens()) {
            boolean grupoCsosn = item.imposto().grupoIcms().startsWith("ICMSSN");
            if (simplesNacional && !grupoCsosn) {
                violacoes.add(new ViolacaoRegra("RVN-005",
                        "Item " + item.numero() + ": emitente do Simples Nacional (CRT " + nfe.emitente().regimeTributario()
                                + ") deve usar grupo CSOSN, nao " + item.imposto().grupoIcms()));
            } else if (!simplesNacional && grupoCsosn) {
                violacoes.add(new ViolacaoRegra("RVN-005",
                        "Item " + item.numero() + ": emitente do Regime Normal (CRT " + nfe.emitente().regimeTributario()
                                + ") deve usar grupo CST, nao " + item.imposto().grupoIcms()));
            }
        }
        return violacoes;
    }
}
