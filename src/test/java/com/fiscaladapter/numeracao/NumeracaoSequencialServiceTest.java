package com.fiscaladapter.numeracao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class NumeracaoSequencialServiceTest {

    @Autowired
    private NumeracaoSequencialService service;

    @Test
    void deveReservarNumerosDiferentesSemConflito() {
        String cnpj = "11222333000181";

        service.reservar(cnpj, "sp", 1, TipoDocumentoFiscal.NFE, 1);
        service.reservar(cnpj, "sp", 1, TipoDocumentoFiscal.NFE, 2);
        service.reservar(cnpj, "sp", 1, TipoDocumentoFiscal.NFE, 3);
        // nenhuma excecao - numeros distintos convivem sem problema
    }

    @Test
    void deveRejeitarNumeroJaReservadoParaOMesmoEmissorSerieETipo() {
        String cnpj = "22333444000155";
        service.reservar(cnpj, "sp", 1, TipoDocumentoFiscal.NFE, 42);

        assertThatThrownBy(() -> service.reservar(cnpj, "sp", 1, TipoDocumentoFiscal.NFE, 42))
                .isInstanceOf(NumeracaoIndisponivelException.class);
    }

    @Test
    void devePermitirOMesmoNumeroEmSeriesOuTiposDiferentes() {
        String cnpj = "44555666000172";

        service.reservar(cnpj, "rj", 1, TipoDocumentoFiscal.NFE, 1);
        service.reservar(cnpj, "rj", 2, TipoDocumentoFiscal.NFE, 1); // serie diferente
        service.reservar(cnpj, "rj", 1, TipoDocumentoFiscal.NFCE, 1); // tipo diferente
        // nenhuma excecao - a chave de unicidade inclui serie e tipo
    }

    @Test
    void naoDevePermitirQueDuasRequisicoesConcorrentesReservemOMesmoNumero() throws Exception {
        String cnpj = "77888999000163";
        int totalRequisicoes = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<Callable<Boolean>> tarefas = IntStream.range(0, totalRequisicoes)
                .<Callable<Boolean>>mapToObj(i -> () -> {
                    try {
                        service.reservar(cnpj, "mg", 1, TipoDocumentoFiscal.CTE, 999);
                        return true;
                    } catch (NumeracaoIndisponivelException e) {
                        return false;
                    }
                })
                .collect(Collectors.toList());

        List<Future<Boolean>> resultados = pool.invokeAll(tarefas);
        pool.shutdown();

        long sucessos = resultados.stream().map(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).filter(Boolean::booleanValue).count();

        assertThat(sucessos).isEqualTo(1);
    }
}
