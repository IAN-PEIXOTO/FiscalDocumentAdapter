package com.fiscaladapter.seguranca;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prova que o client_secret nunca vai para o log (FIS-60) - mesmo em dev, um log agregado ou um
 * profile "dev" ativado por engano num ambiente compartilhado exporia o segredo em texto claro
 * permanentemente. O segredo deve ser gravado so num arquivo local, nunca logado.
 */
class BootstrapClienteDevConfigTest {

    private static final String SEGREDO_SECRETO = "segredo-super-secreto-que-nao-pode-vazar-no-log";

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void prepararCapturaDeLog() {
        logger = (Logger) LoggerFactory.getLogger(BootstrapClienteDevConfig.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void limparCapturaDeLog() {
        logger.detachAppender(appender);
    }

    @Test
    void naoDeveLogarOClientSecretEDeveGravarNumArquivoLocal() throws Exception {
        ClienteApiRepository repository = Mockito.mock(ClienteApiRepository.class);
        when(repository.count()).thenReturn(0L);

        ClienteApiService service = Mockito.mock(ClienteApiService.class);
        ClienteApiService.CredenciaisGeradas credenciais =
                new ClienteApiService.CredenciaisGeradas("cli_teste123", SEGREDO_SECRETO);
        when(service.cadastrar(anyString())).thenReturn(credenciais);

        BootstrapClienteDevConfig config = new BootstrapClienteDevConfig();
        CommandLineRunner runner = config.criarClienteDevSeNecessario(repository, service);
        runner.run();

        List<String> mensagensLogadas = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(mensagensLogadas).noneMatch(msg -> msg.contains(SEGREDO_SECRETO));

        String mensagemUnica = String.join("\n", mensagensLogadas);
        assertThat(mensagemUnica).contains("cli_teste123");

        Path arquivoDeCredenciais = extrairCaminhoDoArquivo(mensagemUnica);
        assertThat(Files.exists(arquivoDeCredenciais)).isTrue();
        String conteudoDoArquivo = Files.readString(arquivoDeCredenciais);
        assertThat(conteudoDoArquivo).contains(SEGREDO_SECRETO).contains("cli_teste123");

        Files.deleteIfExists(arquivoDeCredenciais);
    }

    private Path extrairCaminhoDoArquivo(String mensagemLogada) {
        for (String palavra : mensagemLogada.split("\\s+")) {
            if (palavra.contains("fiscaladapter-dev-credenciais-")) {
                return Path.of(palavra);
            }
        }
        throw new AssertionError("Mensagem logada nao contem o caminho do arquivo de credenciais: " + mensagemLogada);
    }
}
