package com.fiscaladapter.api.nfse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova ponta a ponta, sem mockar o AbrasfNfseClient, que um municipio sem endpoint cadastrado
 * devolve uma mensagem clara (FIS-56, criterio de aceite 3) - a mensagem vem de
 * NfseEndpointRegistry (ja coberta isoladamente por NfseEndpointRegistryTest), aqui provada
 * chegando ate a resposta HTTP via GlobalExceptionHandler.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfseCancelamentoControllerMunicipioNaoSuportadoTest {

    private static final String CNPJ_PRESTADOR = "91888777000155";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente NFS-e municipio nao suportado de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_PRESTADOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    @Test
    void deveDevolverMensagemClaraQuandoMunicipioNaoTemEndpointCadastrado() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfse/cancelamento")
                        .param("codigoIbgeMunicipio", "9999999")
                        .param("numeroNfse", "1")
                        .param("cpfCnpjPrestador", CNPJ_PRESTADOR)
                        .param("codigoMunicipioPrestacao", "9999999")
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("Endpoint de NFS-e nao cadastrado")));
    }

    private String obterAccessToken() throws Exception {
        String resposta = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .param("grant_type", "client_credentials")
                        .param("scope", "nfe"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resposta).get("access_token").asText();
    }
}
