package com.fiscaladapter.certificado;

import com.fiscaladapter.seguranca.AutorizacaoEmissorService;
import com.fiscaladapter.seguranca.CriptografiaEmRepousoService;
import com.fiscaladapter.seguranca.EmissorAutorizado;
import com.fiscaladapter.seguranca.EmissorAutorizadoRepository;
import com.fiscaladapter.seguranca.EmissorNaoAutorizadoException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CertificadoEmissorServiceTest {

    private static final String CHAVE_TESTE = Base64.getEncoder().encodeToString("chave-de-32-bytes-para-testes-11".getBytes());
    private static final String CLIENT_ID = "cli_teste";

    private final CertificadoEmissorRepository repository = Mockito.mock(CertificadoEmissorRepository.class);
    private final CertificadoDigitalService certificadoDigitalService = new CertificadoDigitalService();
    private final CriptografiaEmRepousoService criptografiaEmRepousoService = new CriptografiaEmRepousoService(CHAVE_TESTE);
    private final EmissorAutorizadoRepository emissorAutorizadoRepository = Mockito.mock(EmissorAutorizadoRepository.class);
    private final AutorizacaoEmissorService autorizacaoEmissorService = new AutorizacaoEmissorService(emissorAutorizadoRepository);

    private final CertificadoEmissorService service =
            new CertificadoEmissorService(repository, certificadoDigitalService, criptografiaEmRepousoService, autorizacaoEmissorService);

    @Test
    void deveRegistrarCertificadoNovoCriptografandoP12ESenha() throws Exception {
        byte[] p12 = certificadoDeTeste();
        when(repository.findByCnpj("12345678000199")).thenReturn(Optional.empty());
        when(emissorAutorizadoRepository.findByCnpj("12345678000199")).thenReturn(Optional.empty());

        CertificadoInfo info = service.registrar(CLIENT_ID, p12, "senha123".toCharArray());

        assertThat(info.cnpj()).isEqualTo("12345678000199");
        verify(repository, times(1)).save(any(CertificadoEmissor.class));
    }

    @Test
    void deveAtualizarRegistroExistenteAoReenviarMesmoCnpj() throws Exception {
        byte[] p12 = certificadoDeTeste();
        CertificadoEmissor existente = new CertificadoEmissor("12345678000199",
                new CertificadoInfo("alias-antigo", "CN=antigo", "12345678000199", Instant.now(), Instant.now()),
                "p12-antigo", "senha-antiga");
        when(repository.findByCnpj("12345678000199")).thenReturn(Optional.of(existente));
        when(emissorAutorizadoRepository.findByCnpj("12345678000199"))
                .thenReturn(Optional.of(new EmissorAutorizado("12345678000199", CLIENT_ID)));

        service.registrar(CLIENT_ID, p12, "senha123".toCharArray());

        verify(repository, never()).save(any());
        assertThat(existente.getP12Criptografado()).isNotEqualTo("p12-antigo");
    }

    @Test
    void deveRejeitarRegistroDeClientIdDiferenteDoDonoDoCnpj() throws Exception {
        byte[] p12 = certificadoDeTeste();
        when(emissorAutorizadoRepository.findByCnpj("12345678000199"))
                .thenReturn(Optional.of(new EmissorAutorizado("12345678000199", "outro_cliente")));

        assertThatThrownBy(() -> service.registrar(CLIENT_ID, p12, "senha123".toCharArray()))
                .isInstanceOf(EmissorNaoAutorizadoException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deveCarregarCertificadoRegistradoDescriptografandoP12ESenha() throws Exception {
        byte[] p12 = certificadoDeTeste();
        when(repository.findByCnpj("12345678000199")).thenReturn(Optional.empty());
        when(emissorAutorizadoRepository.findByCnpj("12345678000199")).thenReturn(Optional.empty());
        service.registrar(CLIENT_ID, p12, "senha123".toCharArray());

        org.mockito.ArgumentCaptor<CertificadoEmissor> captor = org.mockito.ArgumentCaptor.forClass(CertificadoEmissor.class);
        verify(repository).save(captor.capture());
        when(repository.findByCnpj("12345678000199")).thenReturn(Optional.of(captor.getValue()));
        when(emissorAutorizadoRepository.findByCnpj("12345678000199"))
                .thenReturn(Optional.of(new EmissorAutorizado("12345678000199", CLIENT_ID)));

        CertificadoCarregado carregado = service.carregar(CLIENT_ID, "12345678000199");

        assertThat(carregado.info().cnpj()).isEqualTo("12345678000199");
        assertThat(carregado.chaveEEntidade().getPrivateKey()).isNotNull();
    }

    @Test
    void deveRejeitarCarregamentoDeClientIdDiferenteDoDonoDoCnpj() {
        when(emissorAutorizadoRepository.findByCnpj("12345678000199"))
                .thenReturn(Optional.of(new EmissorAutorizado("12345678000199", "outro_cliente")));

        assertThatThrownBy(() -> service.carregar(CLIENT_ID, "12345678000199"))
                .isInstanceOf(EmissorNaoAutorizadoException.class);
    }

    @Test
    void deveLancarExcecaoAoCarregarCnpjNaoRegistrado() {
        when(emissorAutorizadoRepository.findByCnpj("00000000000000")).thenReturn(Optional.empty());
        when(repository.findByCnpj("00000000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.carregar(CLIENT_ID, "00000000000000"))
                .isInstanceOf(CertificadoNaoEncontradoException.class);
    }

    @Test
    void deveRemoverRegistroPorCnpj() {
        when(emissorAutorizadoRepository.findByCnpj("12345678000199"))
                .thenReturn(Optional.of(new EmissorAutorizado("12345678000199", CLIENT_ID)));

        service.remover(CLIENT_ID, "12345678000199");

        verify(repository, times(1)).deleteByCnpj("12345678000199");
    }

    private byte[] certificadoDeTeste() throws Exception {
        return TestCertificadoFactory.gerarP12("12345678000199", "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
    }
}
