package com.fiscaladapter.api.nfse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.nfse.AbrasfNfseClient;
import com.fiscaladapter.sefaz.nfse.CancelamentoNfseResponse;
import com.fiscaladapter.sefaz.nfse.NfseResponse;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova o cancelamento e a consulta de status de uma NFS-e ja emitida, via webservice municipal ABRASF (FIS-56). */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfseCancelamentoControllerTest {

    private static final String CNPJ_PRESTADOR = "91888777000144";
    private static final String CODIGO_IBGE_MUNICIPIO = "3550308";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private AbrasfNfseClient abrasfNfseClient;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente NFS-e cancelamento de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_PRESTADOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    @Test
    void deveCancelarNfseComSucesso() throws Exception {
        when(abrasfNfseClient.cancelarNfse(eq(CODIGO_IBGE_MUNICIPIO), eq("789"), eq(CNPJ_PRESTADOR),
                any(), eq(CODIGO_IBGE_MUNICIPIO), any(), any()))
                .thenReturn(new CancelamentoNfseResponse("2026-03-20T10:00:00-03:00", null, null));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfse/cancelamento")
                        .param("codigoIbgeMunicipio", CODIGO_IBGE_MUNICIPIO)
                        .param("numeroNfse", "789")
                        .param("cpfCnpjPrestador", CNPJ_PRESTADOR)
                        .param("codigoMunicipioPrestacao", CODIGO_IBGE_MUNICIPIO)
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelada").value(true))
                .andExpect(jsonPath("$.dataHoraCancelamento").value("2026-03-20T10:00:00-03:00"));
    }

    @Test
    void deveDevolverErroDaPrefeituraQuandoCancelamentoRejeitado() throws Exception {
        when(abrasfNfseClient.cancelarNfse(eq(CODIGO_IBGE_MUNICIPIO), eq("790"), eq(CNPJ_PRESTADOR),
                any(), eq(CODIGO_IBGE_MUNICIPIO), any(), any()))
                .thenReturn(new CancelamentoNfseResponse(null, "E002", "NFS-e ja cancelada anteriormente"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfse/cancelamento")
                        .param("codigoIbgeMunicipio", CODIGO_IBGE_MUNICIPIO)
                        .param("numeroNfse", "790")
                        .param("cpfCnpjPrestador", CNPJ_PRESTADOR)
                        .param("codigoMunicipioPrestacao", CODIGO_IBGE_MUNICIPIO)
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelada").value(false))
                .andExpect(jsonPath("$.mensagemErro").value("NFS-e ja cancelada anteriormente"));
    }

    @Test
    void deveConsultarStatusDaNfsePorRps() throws Exception {
        when(abrasfNfseClient.consultarNfseRps(eq(CODIGO_IBGE_MUNICIPIO), anyLong(), eq("1"), eq(CNPJ_PRESTADOR),
                any(), any(), any()))
                .thenReturn(new NfseResponse("789", "ABC123XYZ", null, null));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfse/consulta")
                        .param("codigoIbgeMunicipio", CODIGO_IBGE_MUNICIPIO)
                        .param("numeroRps", "42")
                        .param("serieRps", "1")
                        .param("cpfCnpjPrestador", CNPJ_PRESTADOR)
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.numeroNfse").value("789"))
                .andExpect(jsonPath("$.codigoVerificacao").value("ABC123XYZ"));
    }

    @Test
    void deveRejeitarRequisicaoSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/nfse/cancelamento")
                        .param("codigoIbgeMunicipio", CODIGO_IBGE_MUNICIPIO)
                        .param("numeroNfse", "791")
                        .param("cpfCnpjPrestador", CNPJ_PRESTADOR)
                        .param("codigoMunicipioPrestacao", CODIGO_IBGE_MUNICIPIO)
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
