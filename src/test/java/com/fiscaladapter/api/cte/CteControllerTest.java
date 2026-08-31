package com.fiscaladapter.api.cte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.EmitRequest;
import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.cte.TipoTomadorServico;
import com.fiscaladapter.sefaz.cte.CteAutorizacaoClient;
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

/** Prova de ponta a ponta da emissao de CT-e via API (FIS-44). */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CteControllerTest {

    private static final String CNPJ_EMISSOR = "33444555000160";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private CteAutorizacaoClient autorizacaoClient;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente CT-e de teste");
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
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso do CT-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00", true));
    }

    @Test
    void deveEmitirCteComSucessoRetornandoNotasTransportadas() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(700L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-cte-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000001"))
                .andExpect(jsonPath("$.notasFiscaisTransportadas[0]").value("35260112345678000199550010000000421000000019"))
                .andExpect(jsonPath("$.dactePdfBase64").isNotEmpty());
    }

    @Test
    void chaveDeAcessoDoCteDeveTerModelo57() throws Exception {
        String accessToken = obterAccessToken();

        String resposta = mockMvc.perform(post("/api/v1/cte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(701L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-cte-002"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chaveAcesso = objectMapper.readTree(resposta).get("chaveAcesso").asText();
        org.assertj.core.api.Assertions.assertThat(chaveAcesso.substring(20, 22)).isEqualTo("57");
    }

    @Test
    void deveRejeitarRequisicaoSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/cte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(702L)))
                        .header("Idempotency-Key", "chave-cte-003"))
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

    private CtePedidoEmissaoRequest pedidoValido(long numero) {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rod. BR-101", "km 10", null, "Distrito Industrial", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitRequest emit = new EmitRequest(CNPJ_EMISSOR, null, "TRANSPORTADORA TESTE LTDA", "TT TRANSPORTES", enderecoEmitente, "111222333", null, null, null, "3");

        EnderecoNfeRequest enderecoRemetente = new EnderecoNfeRequest("Rua Origem", "10", null, "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", null);
        ParticipanteCteRequest remetente = new ParticipanteCteRequest("11222333000144", null, "111222333", "REMETENTE TESTE LTDA", enderecoRemetente, "remetente@teste.com");

        EnderecoNfeRequest enderecoDestinatario = new EnderecoNfeRequest("Rua Destino", "20", null, "Centro", "3304557", "Rio de Janeiro", "RJ", "20000000", "1058", "Brasil", null);
        ParticipanteCteRequest destinatario = new ParticipanteCteRequest("55666777000188", null, "556667770", "DESTINATARIO TESTE LTDA", enderecoDestinatario, "destinatario@teste.com");

        IdeCteRequest ide = new IdeCteRequest("SP", "6353", "PRESTACAO DE SERVICO DE TRANSPORTE", 1, numero,
                LocalDate.of(2026, 3, 15), "3550308", "Sao Paulo", "SP", "3304557", "Rio de Janeiro", "RJ");

        ImpostoCteRequest imp = new ImpostoCteRequest(BigDecimal.valueOf(1000.00), BigDecimal.valueOf(12.00), BigDecimal.valueOf(120.00));
        InformacaoCargaRequest infCarga = new InformacaoCargaRequest(BigDecimal.valueOf(5000.00), "MERCADORIAS DIVERSAS", BigDecimal.valueOf(1500.0000));

        InfCteRequest infCte = new InfCteRequest(ide, emit, remetente, destinatario, TipoTomadorServico.REMETENTE,
                BigDecimal.valueOf(1000.00), BigDecimal.valueOf(1000.00), imp, infCarga,
                List.of(new NotaFiscalTransportadaRequest("35260112345678000199550010000000421000000019")),
                "12345678");

        return new CtePedidoEmissaoRequest("homologacao", "teste-cte", infCte);
    }
}
