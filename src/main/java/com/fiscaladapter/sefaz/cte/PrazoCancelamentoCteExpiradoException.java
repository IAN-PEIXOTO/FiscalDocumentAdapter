package com.fiscaladapter.sefaz.cte;

import java.time.Duration;

/**
 * CT-e so pode ser cancelado dentro de um prazo apos a autorizacao - 168
 * horas (7 dias), pela clausula decima quarta do Ajuste SINIEF 09/07
 * (algumas UFs adotam prazo menor, ex.: MT reduziu para 24h - nao
 * verificado por UF nesta sessao, usa-se o teto nacional). Bloqueado aqui
 * antes de gastar uma tentativa que a SEFAZ rejeitaria de qualquer forma
 * (FIS-44). Cancelamento extemporaneo (apos o prazo) e um procedimento
 * proprio de cada UF, fora do escopo deste adapter.
 */
public class PrazoCancelamentoCteExpiradoException extends RuntimeException {

    public PrazoCancelamentoCteExpiradoException(Duration prazoMaximo, Duration tempoDesdeAAutorizacao) {
        super("Prazo de cancelamento do CT-e expirado: autorizado ha " + tempoDesdeAAutorizacao.toHours()
                + "h, prazo maximo e de " + prazoMaximo.toHours() + "h (Ajuste SINIEF 09/07, clausula 14) - "
                + "cancelamento extemporaneo e um procedimento especifico de cada UF, fora do escopo desta API");
    }
}
