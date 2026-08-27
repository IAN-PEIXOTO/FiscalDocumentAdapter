package com.fiscaladapter.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClienteApiOAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClienteApiService clienteApiService;

    @Test
    void duranteRotacaoAmbosOsSegredosDevemFuncionar() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente rotacao");

        obterToken(credenciais.clientId(), credenciais.clientSecret()).andExpect(status().isOk());

        String novoSegredo = clienteApiService.rotacionarSegredo(credenciais.clientId());

        obterToken(credenciais.clientId(), credenciais.clientSecret()).andExpect(status().isOk());
        obterToken(credenciais.clientId(), novoSegredo).andExpect(status().isOk());
    }

    @Test
    void aposEncerrarRotacaoSegredoAntigoDeveParaDeFuncionar() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente rotacao 2");
        String novoSegredo = clienteApiService.rotacionarSegredo(credenciais.clientId());

        clienteApiService.encerrarRotacao(credenciais.clientId());

        obterToken(credenciais.clientId(), credenciais.clientSecret()).andExpect(status().isUnauthorized());
        obterToken(credenciais.clientId(), novoSegredo).andExpect(status().isOk());
    }

    @Test
    void clienteRevogadoNaoDeveConseguirObterToken() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente revogado");
        obterToken(credenciais.clientId(), credenciais.clientSecret()).andExpect(status().isOk());

        clienteApiService.revogar(credenciais.clientId());

        obterToken(credenciais.clientId(), credenciais.clientSecret()).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions obterToken(String clientId, String clientSecret) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .with(httpBasic(clientId, clientSecret))
                .param("grant_type", "client_credentials")
                .param("scope", "nfe"));
    }
}
