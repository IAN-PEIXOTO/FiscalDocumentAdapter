package com.fiscaladapter.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * So em dev: cria um client_id/client_secret de exemplo se nao existir
 * nenhum cliente cadastrado. Facilita testar a API localmente sem precisar
 * de um endpoint de cadastro (que ainda nao existe - ver observacao no
 * FIS-15 sobre cadastro/gestao de clientes).
 *
 * O client_secret NUNCA vai para o log (FIS-60) - mesmo so em dev, um log
 * agregado (ELK/CloudWatch/etc.) ou um profile "dev" ativado por engano num
 * ambiente compartilhado exporia o segredo em texto claro permanentemente,
 * sem possibilidade de rotacao retroativa do que ja vazou. Em vez disso, e
 * gravado uma unica vez num arquivo local (fora do sistema de logging) - so
 * o caminho do arquivo (nao o segredo) e logado.
 */
@Configuration
@Profile("dev")
public class BootstrapClienteDevConfig {

    private static final Logger log = LoggerFactory.getLogger(BootstrapClienteDevConfig.class);

    @Bean
    public CommandLineRunner criarClienteDevSeNecessario(ClienteApiRepository repository, ClienteApiService service) {
        return args -> {
            if (repository.count() == 0) {
                ClienteApiService.CredenciaisGeradas credenciais = service.cadastrar("Cliente de desenvolvimento");
                Path arquivo = gravarCredenciaisEmArquivoLocal(credenciais);
                log.info("Cliente de API criado para dev - client_id: {} - client_secret gravado em {} "
                                + "(leia agora e apague o arquivo depois; nao sera reescrito nem reexibido)",
                        credenciais.clientId(), arquivo);
            }
        };
    }

    /**
     * FIS-66: application.yml define spring.profiles.active=dev como default e um fallback
     * hardcoded para a chave de criptografia quando SPRING_PROFILES_ACTIVE nao esta definido -
     * conveniente para rodar localmente sem configuracao, mas perigoso se algum dia um deploy
     * real subir sem definir a variavel explicitamente (cairia silenciosamente no H2 em memoria
     * e numa chave AES previsivel). Este aviso torna esse cenario visivel em qualquer agregador
     * de log, mesmo que ninguem tenha notado a falta da variavel de ambiente.
     */
    @Bean
    public CommandLineRunner avisarSobreProfileDev() {
        return args -> log.warn("Rodando com o profile 'dev' ATIVO - H2 em memoria e chave de criptografia "
                + "hardcoded (application.yml), inseguros fora de desenvolvimento local. Se isto nao e uma maquina "
                + "de desenvolvedor, defina SPRING_PROFILES_ACTIVE=homolog ou =prod explicitamente.");
    }

    private Path gravarCredenciaisEmArquivoLocal(ClienteApiService.CredenciaisGeradas credenciais) throws IOException {
        Path arquivo = Files.createTempFile("fiscaladapter-dev-credenciais-", ".txt");
        try {
            Files.setPosixFilePermissions(arquivo, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignorada) {
            // sistema de arquivos sem suporte a permissoes POSIX (ex.: Windows) - restringe via
            // java.io.File como melhor esforco, suficiente para uso local de dev
            arquivo.toFile().setReadable(false, false);
            arquivo.toFile().setReadable(true, true);
            arquivo.toFile().setWritable(false, false);
            arquivo.toFile().setWritable(true, true);
        }
        Files.writeString(arquivo, "client_id: " + credenciais.clientId()
                + "\nclient_secret: " + credenciais.clientSecret() + "\n");
        return arquivo;
    }
}
