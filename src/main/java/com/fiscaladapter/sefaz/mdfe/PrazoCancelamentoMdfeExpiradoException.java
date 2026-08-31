package com.fiscaladapter.sefaz.mdfe;

import java.time.Duration;

/**
 * MDF-e so pode ser cancelado dentro de um prazo apos a autorizacao - 24
 * horas, pelo Ajuste SINIEF 21/2010, desde que o transporte ainda nao
 * tenha iniciado (essa segunda condicao nao e verificavel localmente - fica
 * a cargo da propria SEFAZ rejeitar se for o caso). Bloqueado aqui antes de
 * gastar uma tentativa que a SEFAZ rejeitaria de qualquer forma (FIS-45).
 * Cancelamento extemporaneo (apos o prazo) e um procedimento proprio de
 * cada UF, fora do escopo deste adapter.
 */
public class PrazoCancelamentoMdfeExpiradoException extends RuntimeException {

    public PrazoCancelamentoMdfeExpiradoException(Duration prazoMaximo, Duration tempoDesdeAAutorizacao) {
        super("Prazo de cancelamento do MDF-e expirado: autorizado ha " + tempoDesdeAAutorizacao.toHours()
                + "h, prazo maximo e de " + prazoMaximo.toHours() + "h (Ajuste SINIEF 21/2010) - "
                + "cancelamento extemporaneo e um procedimento especifico de cada UF, fora do escopo desta API");
    }
}
