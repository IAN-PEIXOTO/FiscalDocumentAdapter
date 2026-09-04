package com.fiscaladapter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.Executor;

/**
 * FIS-79: sem esta configuracao, todo @Scheduled da aplicacao (hoje so
 * EmissaoAssincronaWorker.processarPendentes) roda na thread unica padrao do
 * Spring, compartilhada por toda a aplicacao - um lote de jobs demorados
 * atrasaria qualquer outro @Scheduled futuro, e um poll ainda em andamento
 * atrasaria o proximo ciclo inteiro.
 *
 * webhookExecutor e um pool separado, dedicado so a notificacao de webhook
 * (WebhookNotificacaoAssincronaService) - a notificacao pode levar ate ~14s
 * de backoff entre tentativas (2s+4s+8s) e nao deve segurar a thread do
 * scheduler que processa os proximos jobs da fila.
 */
@Configuration
@EnableAsync
public class SchedulingConfig implements SchedulingConfigurer {

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("fiscaladapter-scheduler-");
        scheduler.initialize();
        taskRegistrar.setTaskScheduler(scheduler);
    }

    @Bean(name = "webhookExecutor")
    public Executor webhookExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("fiscaladapter-webhook-");
        executor.initialize();
        return executor;
    }
}
