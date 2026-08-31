package com.fiscaladapter.api.mdfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.mdfe.MdfeAutorizacaoClient;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova de ponta a ponta da emissao de MDF-e via API (FIS-45). */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MdfeControllerTest {

    private static final String CNPJ_EMISSOR = "77888999000112";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private MdfeAutorizacaoClient autorizacaoClient;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente MDF-e de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    @BeforeEach
    void prepararMocks() {
        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso do MDF-e", "935260000000001",
                        "2026-03-15T10:00:00-03:00", true));
    }

    @Test
    void deveEmitirMdfeComSucesso() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(900L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-mdfe-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("935260000000001"))
                .andExpect(jsonPath("$.damdfePdfBase64").isNotEmpty());
    }

    @Test
    void chaveDeAcessoDoMdfeDeveTerModelo58() throws Exception {
        String accessToken = obterAccessToken();

        String resposta = mockMvc.perform(post("/api/v1/mdfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(901L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-mdfe-002"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chaveAcesso = objectMapper.readTree(resposta).get("chaveAcesso").asText();
        org.assertj.core.api.Assertions.assertThat(chaveAcesso.substring(20, 22)).isEqualTo("58");
    }

    @Test
    void deveRejeitarRequisicaoSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/mdfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(902L)))
                        .header("Idempotency-Key", "chave-mdfe-003"))
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

    private MdfePedidoEmissaoRequest pedidoValido(long numero) {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rod. BR-101", "km 10", null, "Distrito Industrial", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitMdfeRequest emit = new EmitMdfeRequest(CNPJ_EMISSOR, "TRANSPORTADORA TESTE LTDA", "TT TRANSPORTES", "111222333", enderecoEmitente);

        IdeMdfeRequest ide = new IdeMdfeRequest("SP", "SP", "RJ", 1, numero, LocalDate.of(2026, 3, 15), "3550308", "Sao Paulo");

        VeiculoTracaoRequest veicTracao = new VeiculoTracaoRequest("ABC1D23", BigDecimal.valueOf(8000), "03", "02", "SP");
        List<CondutorRequest> condutores = List.of(new CondutorRequest("JOAO DA SILVA", "12345678900"));

        InfMdfeRequest infMDFe = new InfMdfeRequest(ide, emit, "12345678", veicTracao, condutores,
                "3304557", "Rio de Janeiro",
                List.of("35260112345678000199570010000000421000000012"),
                List.of("35260112345678000199550010000000421000000019"),
                BigDecimal.valueOf(5000.00), BigDecimal.valueOf(1500.0000));

        return new MdfePedidoEmissaoRequest("homologacao", "teste-mdfe", infMDFe);
    }
}
