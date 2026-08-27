package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.CceResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.sefaz.nfe.InutilizacaoResponse;
import com.fiscaladapter.sefaz.nfe.NfeCancelamentoClient;
import com.fiscaladapter.sefaz.nfe.NfeCceClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
import com.fiscaladapter.sefaz.nfe.NfeInutilizacaoClient;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Certificado resolvido pelo CNPJ do emitente extraido da chave de acesso
 * (FIS-2) - registrado uma vez em prepararCenario(), sem multipart por chamada.
 */
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

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private NfeConsultaProtocoloClient consultaProtocoloClient;

    @MockBean
    private NfeCancelamentoClient cancelamentoClient;

    @MockBean
    private NfeCceClient cceClient;

    @MockBean
    private NfeInutilizacaoClient inutilizacaoClient;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void prepararCenario() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente de teste consulta");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(p12, "senha123".toCharArray());
    }

    @Test
    void deveConsultarNfeERetornarSituacao() throws Exception {
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e", "135260000000001"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe/" + CHAVE_ACESSO + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
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

        mockMvc.perform(post("/api/v1/nfe/" + CHAVE_ACESSO + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Erro de digitacao no valor do produto")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelado").value(true));
    }

    @Test
    void deveEmitirCceERetornarProtocolo() throws Exception {
        when(cceClient.corrigir(any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(CceResponse.de("135", "Evento registrado e vinculado a NF-e", "135260000000002"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe/" + CHAVE_ACESSO + "/cartaCorrecao")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroSequencial", "1")
                        .param("textoCorrecao", "Correcao do endereco de entrega, sem alteracao de valores")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000002"));
    }

    @Test
    void deveInutilizarFaixaDeNumeracaoERetornarConfirmacao() throws Exception {
        when(inutilizacaoClient.inutilizar(any(), any(), anyInt(), anyLong(), anyLong(), any(), any(), any()))
                .thenReturn(InutilizacaoResponse.de("102", "Inutilizacao de numero homologada", "135260000000003"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe/inutilizacao")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("cnpjEmitente", "12345678000199")
                        .param("serie", "1")
                        .param("numeroInicial", "100")
                        .param("numeroFinal", "110")
                        .param("justificativa", "Numeracao pulada por erro de sistema antes da transmissao")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inutilizada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000003"));
    }

    @Test
    void deveRejeitarConsultaSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/nfe/" + CHAVE_ACESSO + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO"))
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
}
