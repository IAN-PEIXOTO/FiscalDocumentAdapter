package com.fiscaladapter.sefaz.nfe;

import java.time.Duration;

/**
 * NFC-e (modelo 65) so pode ser cancelada dentro de um prazo curto apos a
 * autorizacao - 30 minutos, pelo Ajuste SINIEF 07/18 (reduzido dos 24h
 * anteriores; alguns estados adotam um prazo ainda menor, nunca maior).
 * Bloqueado aqui antes de gastar uma tentativa de cancelamento que a SEFAZ
 * rejeitaria de qualquer forma (FIS-43).
 */
public class PrazoCancelamentoNfceExpiradoException extends RuntimeException {

    public PrazoCancelamentoNfceExpiradoException(Duration prazoMaximo, Duration tempoDesdeAAutorizacao) {
        super("Prazo de cancelamento da NFC-e expirado: autorizada ha " + tempoDesdeAAutorizacao.toMinutes()
                + " min, prazo maximo e de " + prazoMaximo.toMinutes() + " min (Ajuste SINIEF 07/18)");
    }
}
