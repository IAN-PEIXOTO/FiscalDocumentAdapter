package com.fiscaladapter.observabilidade;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Metricas Prometheus (via Micrometer) do fluxo de emissao de NFe (FIS-11):
 * quantas notas sao autorizadas, rejeitadas pela SEFAZ (por cStat) ou falham
 * por erro de comunicacao, e quantas precisam de contingencia (SVC). Exposto
 * em /actuator/prometheus.
 */
@Component
public class NfeEmissaoMetrics {

    private static final String COUNTER_EMISSAO = "fiscaladapter.nfe.emissao";
    private static final String TIMER_EMISSAO = "fiscaladapter.nfe.emissao.duracao";

    private final MeterRegistry meterRegistry;

    public NfeEmissaoMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample iniciarCronometro() {
        return Timer.start(meterRegistry);
    }

    public void registrarAutorizada(Timer.Sample cronometro, boolean viaContingencia) {
        finalizar(cronometro, "autorizada", viaContingencia, null);
    }

    public void registrarRejeitada(Timer.Sample cronometro, boolean viaContingencia, String codigoStatusSefaz) {
        finalizar(cronometro, "rejeitada", viaContingencia, codigoStatusSefaz);
    }

    public void registrarErroComunicacao(Timer.Sample cronometro) {
        finalizar(cronometro, "erro_comunicacao", false, null);
    }

    /** EPEC registrado (ultimo recurso): NFe ainda nao autorizada de fato, so liberada provisoriamente. */
    public void registrarViaEpec(Timer.Sample cronometro, String codigoStatusSefaz) {
        finalizar(cronometro, "epec_provisoria", true, codigoStatusSefaz);
    }

    private void finalizar(Timer.Sample cronometro, String resultado, boolean viaContingencia, String codigoStatusSefaz) {
        Counter.builder(COUNTER_EMISSAO)
                .tag("resultado", resultado)
                .tag("contingencia", Boolean.toString(viaContingencia))
                .tag("cstat_sefaz", codigoStatusSefaz == null ? "n/a" : codigoStatusSefaz)
                .register(meterRegistry)
                .increment();

        cronometro.stop(Timer.builder(TIMER_EMISSAO)
                .tag("resultado", resultado)
                .register(meterRegistry));
    }
}
