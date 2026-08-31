package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.sefaz.nfe.NfeCancelamentoClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova o prazo de cancelamento especifico da NFC-e (FIS-43): 30 min a partir da autorizacao (Ajuste SINIEF 07/18). */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfceCancelamentoPrazoTest {

    private static final String CNPJ_EMISSOR = "55666777000199";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @Autowired
    private ChaveAcessoService chaveAcessoService;

    @MockBean
    private NfeConsultaProtocoloClient consultaProtocoloClient;

    @MockBean
    private NfeCancelamentoClient cancelamentoClient;

    private String clientId;
    private String clientSecret;
    private String chaveAcessoNfce;

    @BeforeAll
    void prepararCenario() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente NFC-e cancelamento de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());

        this.chaveAcessoNfce = chaveAcessoService.gerar("SP", LocalDate.now(), CNPJ_EMISSOR,
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFCE), 1, 999L, 1);
    }

    @Test
    void deveCancelarNfceDentroDoPrazoDe30Minutos() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusMinutes(10).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e", "135260000000001", dhRecbto));
        when(cancelamentoClient.cancelar(any(), any(), any(), any(), any(), any()))
                .thenReturn(CancelamentoResponse.de("135", "Evento registrado e vinculado a NF-e"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe/" + chaveAcessoNfce + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Cliente desistiu da compra")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelado").value(true));
    }

    @Test
    void deveRejeitarCancelamentoDeNfceForaDoPrazoDe30Minutos() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusMinutes(45).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso da NF-e", "135260000000001", dhRecbto));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe/" + chaveAcessoNfce + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Cliente desistiu da compra")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("Prazo de cancelamento da NFC-e expirado")));
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
