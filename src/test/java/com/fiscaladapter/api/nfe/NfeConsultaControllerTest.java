package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.sefaz.nfe.NfeCancelamentoClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NfeConsultaControllerTest {

    private static final String CHAVE_ACESSO = "35260012345678000199550010000000421000000010";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @MockBean
    private NfeConsultaProtocoloClient consultaProtocoloClient;

    @MockBean
    private NfeCancelamentoClient cancelamentoClient;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void criarClienteDeTeste() {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente de teste consulta");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();
    }

    @Test
    void deveConsultarNfeERetornarSituacao() throws Exception {
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e", "135260000000001"));

        String accessToken = obterAccessToken();
        MockMultipartFile certificado = new MockMultipartFile("certificado", "c.p12", "application/x-pkcs12", certificadoDeTeste());

        mockMvc.perform(multipart("/api/v1/nfe/" + CHAVE_ACESSO + "/consulta")
                        .file(certificado)
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("senhaCertificado", "senha123")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000001"));
    }

    @Test
    void deveCancelarNfeERetornarConfirmacao() throws Exception {
        when(cancelamentoClient.cancelar(any(), any(), any(), any(), any(), any()))
                .thenReturn(CancelamentoResponse.de("135", "Evento registrado e vinculado a NF-e"));

        String accessToken = obterAccessToken();
        MockMultipartFile certificado = new MockMultipartFile("certificado", "c.p12", "application/x-pkcs12", certificadoDeTeste());

        mockMvc.perform(multipart("/api/v1/nfe/" + CHAVE_ACESSO + "/cancelamento")
                        .file(certificado)
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Erro de digitacao no valor do produto")
                        .param("senhaCertificado", "senha123")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelado").value(true));
    }

    @Test
    void deveRejeitarConsultaSemToken() throws Exception {
        MockMultipartFile certificado = new MockMultipartFile("certificado", "c.p12", "application/x-pkcs12", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/nfe/" + CHAVE_ACESSO + "/consulta")
                        .file(certificado)
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("senhaCertificado", "qualquer"))
                .andExpect(status().isUnauthorized());
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

    private byte[] certificadoDeTeste() throws Exception {
        return TestCertificadoFactory.gerarP12("12345678000199", "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
    }
}
