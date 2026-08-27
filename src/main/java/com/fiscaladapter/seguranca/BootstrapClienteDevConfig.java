package com.fiscaladapter.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * So em dev: cria um client_id/client_secret de exemplo se nao existir
 * nenhum cliente cadastrado, e loga o segredo uma unica vez. Facilita testar
 * a API localmente sem precisar de um endpoint de cadastro (que ainda nao
 * existe - ver observacao no FIS-15 sobre cadastro/gestao de clientes).
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
                log.info("Cliente de API criado para dev - client_id: {} | client_secret: {} (guarde agora, nao sera exibido de novo)",
                        credenciais.clientId(), credenciais.clientSecret());
            }
        };
    }
}
