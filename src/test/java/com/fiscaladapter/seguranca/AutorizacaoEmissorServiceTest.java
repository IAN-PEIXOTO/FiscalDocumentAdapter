package com.fiscaladapter.seguranca;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutorizacaoEmissorServiceTest {

    private static final String CNPJ = "12345678000199";

    private final EmissorAutorizadoRepository repository = Mockito.mock(EmissorAutorizadoRepository.class);
    private final AutorizacaoEmissorService service = new AutorizacaoEmissorService(repository);

    @Test
    void deveReivindicarCnpjNaoRegistradoParaOClientIdQueChamou() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());

        service.garantirAutorizacao("cli_a", CNPJ);

        verify(repository, times(1)).save(any(EmissorAutorizado.class));
    }

    @Test
    void deveAceitarReenvioPeloMesmoDono() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.of(new EmissorAutorizado(CNPJ, "cli_a")));

        assertThatCode(() -> service.garantirAutorizacao("cli_a", CNPJ)).doesNotThrowAnyException();
        verify(repository, never()).save(any());
    }

    @Test
    void deveRejeitarReivindicacaoDeCnpjJaPertencenteAOutroCliente() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.of(new EmissorAutorizado(CNPJ, "cli_a")));

        assertThatThrownBy(() -> service.garantirAutorizacao("cli_b", CNPJ))
                .isInstanceOf(EmissorNaoAutorizadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void naoDeveLancarExcecaoAoValidarAcessoDeCnpjNuncaReivindicado() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.empty());

        assertThatCode(() -> service.validarAcesso("cli_a", CNPJ)).doesNotThrowAnyException();
    }

    @Test
    void deveValidarAcessoDoDonoSemLancarExcecao() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.of(new EmissorAutorizado(CNPJ, "cli_a")));

        assertThatCode(() -> service.validarAcesso("cli_a", CNPJ)).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarAcessoDeClienteQueNaoEODono() {
        when(repository.findByCnpj(CNPJ)).thenReturn(Optional.of(new EmissorAutorizado(CNPJ, "cli_a")));

        assertThatThrownBy(() -> service.validarAcesso("cli_b", CNPJ))
                .isInstanceOf(EmissorNaoAutorizadoException.class);
    }
}
