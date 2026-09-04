package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.FusoHorarioFiscal;
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
        // FIS-94: "hoje" precisa ser sempre o dia em Brasilia, nao o fuso do JVM/SO (mesma classe
        // de bug do FIS-84) - senao, perto da meia-noite, um deploy em fuso atrasado em relacao ao
        // Brasil rejeitaria como "futura" uma emissao com data correta.
        if (dataEmissao.isAfter(LocalDate.now(FusoHorarioFiscal.BRASIL))) {
            return List.of(new ViolacaoRegra("RVN-006",
                    "Data de emissao (" + dataEmissao + ") nao pode ser posterior a data atual"));
        }
        return List.of();
    }
}
