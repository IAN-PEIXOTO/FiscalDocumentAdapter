package com.fiscaladapter.api.idempotencia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova o desbloqueio de requisicoes presas em PROCESSANDO (FIS-65) - sem isso, um processo que
 * cai entre transmitir a SEFAZ e gravar a resposta bloquearia a mesma Idempotency-Key com 409
 * para sempre (o status PROCESSANDO e checado antes da janela de 24h do cache de resposta).
 */
@SpringBootTest
class IdempotenciaServiceTest {

    private static final String TIPO_OPERACAO = String.class.getSimpleName();

    @Autowired
    private IdempotenciaService service;

    @Autowired
    private RequisicaoIdempotenteRepository repository;

    @Test
    void deveBloquearComQuandoAindaDentroDoTempoLimiteDeProcessamento() {
        String clientId = "cliente-idempotencia-1";
        String chave = "chave-recente";
        Instant criadoEm = Instant.now().minusSeconds(30);
        repository.save(new RequisicaoIdempotente(clientId, TIPO_OPERACAO, chave, criadoEm,
                criadoEm.plus(IdempotenciaService.JANELA_VALIDADE)));

        assertThatThrownBy(() -> service.executar(clientId, chave, String.class, () -> "novo"))
                .isInstanceOf(RequisicaoEmProcessamentoException.class);
    }

    @Test
    void deveLiberarReprocessamentoQuandoRequisicaoFicaPresaProcessandoPorMuitoTempo() {
        String clientId = "cliente-idempotencia-2";
        String chave = "chave-presa";
        Instant criadoEm = Instant.now().minus(IdempotenciaService.TEMPO_LIMITE_PROCESSANDO).minusSeconds(60);
        repository.save(new RequisicaoIdempotente(clientId, TIPO_OPERACAO, chave, criadoEm,
                criadoEm.plus(IdempotenciaService.JANELA_VALIDADE)));

        AtomicInteger chamadas = new AtomicInteger(0);
        String resultado = service.executar(clientId, chave, String.class, () -> {
            chamadas.incrementAndGet();
            return "reprocessado";
        });

        assertThat(resultado).isEqualTo("reprocessado");
        assertThat(chamadas.get()).isEqualTo(1);
    }

    /** FIS-81: expurgo periodico deve remover linhas expiradas, mas nunca as que ainda estao dentro da janela. */
    @Test
    void expurgarExpiradasDeveRemoverSoAsLinhasComJanelaVencida() {
        String clientId = "cliente-idempotencia-3";
        Instant agora = Instant.now();

        RequisicaoIdempotente expirada = repository.save(new RequisicaoIdempotente(
                clientId, TIPO_OPERACAO, "chave-expirada", agora.minus(IdempotenciaService.JANELA_VALIDADE).minusSeconds(60),
                agora.minusSeconds(30)));
        RequisicaoIdempotente aindaValida = repository.save(new RequisicaoIdempotente(
                clientId, TIPO_OPERACAO, "chave-ainda-valida", agora, agora.plus(IdempotenciaService.JANELA_VALIDADE)));

        service.expurgarExpiradas();

        assertThat(repository.findById(expirada.getId())).isEmpty();
        assertThat(repository.findById(aindaValida.getId())).isPresent();
    }
}
