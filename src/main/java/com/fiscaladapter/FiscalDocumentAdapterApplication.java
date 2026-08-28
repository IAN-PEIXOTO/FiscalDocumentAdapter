package com.fiscaladapter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling liga o worker de processamento assincrono de emissao (FIS-25, ver EmissaoAssincronaWorker). */
@SpringBootApplication
@EnableScheduling
public class FiscalDocumentAdapterApplication {

    public static void main(String[] args) {
        SpringApplication.run(FiscalDocumentAdapterApplication.class, args);
    }
}
