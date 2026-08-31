package com.fiscaladapter.distribuicao;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.nfe.NfeDistribuicaoDfeClient;
import com.fiscaladapter.sefaz.nfe.ResumoNfeDistribuicao;
import com.fiscaladapter.sefaz.nfe.RetornoDistribuicaoDfe;
import com.fiscaladapter.sefaz.nfe.SituacaoNfeDistribuicao;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova de ponta a ponta da consulta de NF-e destinadas (FIS-40): autenticacao, multi-tenant e formato da resposta. */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DistribuicaoDfeControllerTest {

    private static final String CNPJ_DESTINATARIO = "44555666000177";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private NfeDistribuicaoDfeClient distribuicaoDfeClient;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente destinatario de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_DESTINATARIO, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    @Test
    void deveListarNfeDestinadasComPrazoDeManifestacaoCalculado() throws Exception {
        OffsetDateTime dataAutorizacao = OffsetDateTime.now().minusDays(80);
        ResumoNfeDistribuicao resumo = new ResumoNfeDistribuicao(
                "35260011222333000181550010000004211000000010", "11222333000181", "Fornecedor Exemplo",
                dataAutorizacao, dataAutorizacao, new BigDecimal("999.90"), SituacaoNfeDistribuicao.AUTORIZADA);

        when(distribuicaoDfeClient.consultarPorNsu(anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new RetornoDistribuicaoDfe("138", "Documento localizado",
                        "000000000000001", "000000000000001", List.of(resumo)));

        String accessToken = obterAccessToken();

        mockMvc.perform(get("/api/v1/nfe/destinadas")
                        .param("cnpjDestinatario", CNPJ_DESTINATARIO)
                        .param("uf", "SP")
                        .param("ambiente", TipoAmbiente.HOMOLOGACAO.name())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chaveAcesso").value(resumo.chaveAcesso()))
                .andExpect(jsonPath("$[0].cnpjEmitente").value("11222333000181"))
                .andExpect(jsonPath("$[0].situacao").value("AUTORIZADA"))
                .andExpect(jsonPath("$[0].prazoExpirado").value(false))
                .andExpect(jsonPath("$[0].alertaProximoDoPrazo").value(true))
                .andExpect(jsonPath("$[0].diasRestantesParaManifestar").value(10));
    }

    @Test
    void deveRetornarForbiddenQuandoOutroClienteConsultaCnpjQueNaoLhePertence() throws Exception {
        ClienteApiService.CredenciaisGeradas outroCliente = clienteApiService.cadastrar("Outro tenant de distribuicao");
        String outroAccessToken = obterAccessToken(outroCliente.clientId(), outroCliente.clientSecret());

        mockMvc.perform(get("/api/v1/nfe/destinadas")
                        .param("cnpjDestinatario", CNPJ_DESTINATARIO)
                        .param("uf", "SP")
                        .param("ambiente", TipoAmbiente.HOMOLOGACAO.name())
                        .header("Authorization", "Bearer " + outroAccessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRetornarUnauthorizedSemToken() throws Exception {
        mockMvc.perform(get("/api/v1/nfe/destinadas")
                        .param("cnpjDestinatario", CNPJ_DESTINATARIO)
                        .param("uf", "SP")
                        .param("ambiente", TipoAmbiente.HOMOLOGACAO.name()))
                .andExpect(status().isUnauthorized());
    }

    private String obterAccessToken() throws Exception {
        return obterAccessToken(clientId, clientSecret);
    }

    private String obterAccessToken(String clientId, String clientSecret) throws Exception {
        String resposta = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .param("grant_type", "client_credentials")
                        .param("scope", "nfe"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(resposta).get("access_token").asText();
    }
}
