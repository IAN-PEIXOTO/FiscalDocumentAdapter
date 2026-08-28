package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * A data de emissao (dhEmi) nao pode ser posterior a data/hora atual - a
 * SEFAZ rejeita a nota nesse caso (data de emissao futura em relacao ao
 * recebimento). Comparacao por data (nao hora) porque o dominio so guarda
 * LocalDate para a data de emissao.
 */
@Component
public class RegraDataEmissaoNaoFutura implements RegraNegocio {

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        LocalDate dataEmissao = nfe.identificacao().dataEmissao();
        if (dataEmissao.isAfter(LocalDate.now())) {
            return List.of(new ViolacaoRegra("RVN-006",
                    "Data de emissao (" + dataEmissao + ") nao pode ser posterior a data atual"));
        }
        return List.of();
    }
}
