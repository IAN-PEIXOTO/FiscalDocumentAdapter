package com.fiscaladapter.distribuicao;

import java.time.Duration;

/**
 * A SEFAZ trata consultas repetidas em curto intervalo como "Consumo
 * Indevido" (cStat 656) - o adapter bloqueia antes de gastar essa tentativa
 * (FIS-40), em vez de deixar a SEFAZ rejeitar.
 */
public class ConsultaDistribuicaoDfeMuitoFrequenteException extends RuntimeException {

    public ConsultaDistribuicaoDfeMuitoFrequenteException(Duration intervaloMinimo, Duration tempoDesdeUltimaConsulta) {
        super("Consulta de NF-e destinadas feita ha " + tempoDesdeUltimaConsulta.toMinutes()
                + " min - aguarde pelo menos " + intervaloMinimo.toMinutes()
                + " min entre consultas para evitar rejeicao da SEFAZ por consumo indevido (cStat 656)");
    }
}
