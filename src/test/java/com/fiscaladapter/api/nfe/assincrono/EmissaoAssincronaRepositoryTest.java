package com.fiscaladapter.api.nfe.assincrono;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova a reivindicacao atomica de um job PENDENTE (FIS-74) - sem isso, duas instancias da
 * aplicacao (ou dois ciclos de poll concorrentes) processariam o mesmo job em duplicidade.
 */
@SpringBootTest
class EmissaoAssincronaRepositoryTest {

    @Autowired
    private EmissaoAssincronaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void apenasUmaChamadaConcorrenteDeveReivindicarOMesmoJob() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long jobId = transactionTemplate.execute(status -> repository.save(
                new EmissaoAssincrona("cliente-fis-74", "idem-fis-74", "{}", Instant.now())).getId());

        Callable<Integer> reivindicar = () -> transactionTemplate.execute(status ->
                repository.reivindicarSePendente(jobId, Instant.now()));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> resultados = executor.invokeAll(List.of(reivindicar, reivindicar));
            int totalReivindicado = resultados.get(0).get() + resultados.get(1).get();

            assertThat(totalReivindicado).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void naoDeveReivindicarJobQueNaoEstaMaisPendente() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Long jobId = transactionTemplate.execute(status -> repository.save(
                new EmissaoAssincrona("cliente-fis-74-b", "idem-fis-74-b", "{}", Instant.now())).getId());

        int primeiraReivindicacao = transactionTemplate.execute(status -> repository.reivindicarSePendente(jobId, Instant.now()));
        int segundaReivindicacao = transactionTemplate.execute(status -> repository.reivindicarSePendente(jobId, Instant.now()));

        assertThat(primeiraReivindicacao).isEqualTo(1);
        assertThat(segundaReivindicacao).isEqualTo(0);
    }
}
