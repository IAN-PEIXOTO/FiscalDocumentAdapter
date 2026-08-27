package com.fiscaladapter.seguranca;

import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Traduz ClienteApi (nosso cadastro, com hash bcrypt e suporte a rotacao de
 * segredo) para o modelo RegisteredClient exigido pelo Spring Authorization
 * Server. Um cliente revogado (ativo=false) simplesmente "nao existe" aqui,
 * o que faz o Authorization Server rejeitar a emissao de novos tokens
 * imediatamente.
 */
@Component
public class ClienteApiRegisteredClientRepository implements RegisteredClientRepository {

    private final ClienteApiRepository repository;

    public ClienteApiRegisteredClientRepository(ClienteApiRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(RegisteredClient registeredClient) {
        throw new UnsupportedOperationException(
                "Clientes sao cadastrados via ClienteApiService, nao pelo Authorization Server");
    }

    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        return repository.findByClientId(clientId)
                .filter(ClienteApi::isAtivo)
                .map(this::paraRegisteredClient)
                .orElse(null);
    }

    private RegisteredClient paraRegisteredClient(ClienteApi cliente) {
        return RegisteredClient.withId(cliente.getClientId())
                .clientId(cliente.getClientId())
                .clientSecret(cliente.secretHashCombinado())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("nfe")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofMinutes(15))
                        .build())
                .build();
    }
}
