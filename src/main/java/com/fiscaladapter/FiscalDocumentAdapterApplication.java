package com.fiscaladapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.Security;

/** @EnableScheduling liga o worker de processamento assincrono de emissao (FIS-25, ver EmissaoAssincronaWorker). */
@SpringBootApplication
@EnableScheduling
public class FiscalDocumentAdapterApplication {

    public static void main(String[] args) {
        fixarCacheDeDnsPositivo();
        SpringApplication.run(FiscalDocumentAdapterApplication.class, args);
    }

    /**
     * FIS-105: sem isso, o comportamento de cache de resolucao DNS da JVM depende de um default
     * interno nao documentado em java.security (networkaddress.cache.ttl vem comentado no JDK) -
     * 30s sem SecurityManager (o caso normal aqui), mas isso pode variar por vendor/versao de JDK
     * ou mudar num upgrade futuro sem aviso. Fixar explicitamente garante que duas resolucoes do
     * mesmo hostname feitas com poucos milissegundos de diferenca (ex.: WebhookUrlValidator
     * validando um endereco e o HttpClient conectando logo em seguida, ver WebhookNotifierService)
     * sempre batem no mesmo resultado em cache, fechando na pratica a corrida de DNS rebinding que
     * a revalidacao por tentativa do FIS-90 sozinha nao elimina (duas resolucoes independentes
     * poderiam, em tese, receber respostas diferentes de um DNS malicioso). Precisa rodar antes de
     * qualquer resolucao de nome feita pelo Spring Boot durante o startup, por isso e a primeira
     * linha de main() - definir a propriedade depois que o contexto ja subiu seria tarde demais
     * para as primeiras resolucoes.
     */
    /** Pacote-privado para ser exercitado diretamente pelo teste, sem precisar subir o Spring Boot inteiro. */
    static void fixarCacheDeDnsPositivo() {
        Security.setProperty("networkaddress.cache.ttl", "30");
    }
}
