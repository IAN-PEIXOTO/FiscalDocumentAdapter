package com.fiscaladapter.numeracao;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NumeracaoSequencialServiceTest {

    @Autowired
    private NumeracaoSequencialService service;

    @Test
    void deveGerarNumerosSequenciaisCrescentes() {
        String cnpj = "11222333000181";

        long primeiro = service.proximoNumero(cnpj, "sp", 1, TipoDocumentoFiscal.NFE);
        long segundo = service.proximoNumero(cnpj, "sp", 1, TipoDocumentoFiscal.NFE);
        long terceiro = service.proximoNumero(cnpj, "sp", 1, TipoDocumentoFiscal.NFE);

        assertThat(List.of(primeiro, segundo, terceiro)).containsExactly(1L, 2L, 3L);
    }

    @Test
    void deveManterSequenciasIndependentesPorSerieETipo() {
        String cnpj = "44555666000172";

        long serie1 = service.proximoNumero(cnpj, "rj", 1, TipoDocumentoFiscal.NFE);
        long serie2 = service.proximoNumero(cnpj, "rj", 2, TipoDocumentoFiscal.NFE);
        long outroTipo = service.proximoNumero(cnpj, "rj", 1, TipoDocumentoFiscal.NFCE);

        assertThat(serie1).isEqualTo(1L);
        assertThat(serie2).isEqualTo(1L);
        assertThat(outroTipo).isEqualTo(1L);
    }

    @Test
    void naoDeveGerarNumerosDuplicadosSobConcorrencia() throws Exception {
        String cnpj = "77888999000163";
        int totalRequisicoes = 50;
        ExecutorService pool = Executors.newFixedThreadPool(10);

        List<Callable<Long>> tarefas = IntStream.range(0, totalRequisicoes)
                .<Callable<Long>>mapToObj(i -> () -> service.proximoNumero(cnpj, "mg", 1, TipoDocumentoFiscal.CTE))
                .collect(Collectors.toList());

        List<Future<Long>> resultados = pool.invokeAll(tarefas);
        pool.shutdown();

        Set<Long> numerosGerados = resultados.stream()
                .map(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toSet());

        assertThat(numerosGerados).hasSize(totalRequisicoes);
        assertThat(numerosGerados).containsExactlyInAnyOrderElementsOf(
                LongRange.of(1, totalRequisicoes)
        );
    }

    private static final class LongRange {
        static List<Long> of(long start, int count) {
            return java.util.stream.LongStream.rangeClosed(start, count).boxed().collect(Collectors.toList());
        }
    }
}
