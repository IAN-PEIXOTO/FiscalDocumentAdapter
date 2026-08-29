package com.fiscaladapter.api.nfe.assincrono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.CofinsAliqRequest;
import com.fiscaladapter.api.nfe.CofinsRequest;
import com.fiscaladapter.api.nfe.DestRequest;
import com.fiscaladapter.api.nfe.DetPagRequest;
import com.fiscaladapter.api.nfe.DetRequest;
import com.fiscaladapter.api.nfe.EmitRequest;
import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.api.nfe.IcmsRequest;
import com.fiscaladapter.api.nfe.Icms00Request;
import com.fiscaladapter.api.nfe.IdeRequest;
import com.fiscaladapter.api.nfe.ImpostoRequest;
import com.fiscaladapter.api.nfe.InfNfeRequest;
import com.fiscaladapter.api.nfe.NfePedidoEmissaoRequest;
import com.fiscaladapter.api.nfe.PagRequest;
import com.fiscaladapter.api.nfe.PisAliqRequest;
import com.fiscaladapter.api.nfe.PisRequest;
import com.fiscaladapter.api.nfe.ProdRequest;
import com.fiscaladapter.api.nfe.TranspRequest;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.nfe.NfeAutorizacaoClient;
import com.fiscaladapter.seguranca.ClienteApiService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova de ponta a ponta do processamento assincrono (FIS-25): enfileira via
 * POST /api/v1/nfe/assincrono, processa a fila chamando
 * EmissaoAssincronaWorker diretamente (em vez de esperar o @Scheduled real -
 * mais rapido e deterministico), confere o status via GET e confirma que o
 * webhook cadastrado recebeu a notificacao.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmissaoAssincronaControllerTest {

    private static final String CNPJ_EMISSOR = "22333444000155";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @Autowired
    private EmissaoAssincronaWorker worker;

    @MockBean
    private NfeAutorizacaoClient autorizacaoClient;

    private String clientId;
    private String clientSecret;
    private static String webhookSecret;
    private static HttpServer servidorWebhook;
    private static BlockingQueue<String> requisicoesRecebidas;
    private static BlockingQueue<String> assinaturasRecebidas;

    @BeforeAll
    void prepararClienteCertificadoEWebhook() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente assincrono de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());

        requisicoesRecebidas = new ArrayBlockingQueue<>(10);
        assinaturasRecebidas = new ArrayBlockingQueue<>(10);
        servidorWebhook = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidorWebhook.createContext("/webhook", exchange -> {
            String corpo = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requisicoesRecebidas.add(corpo);
            assinaturasRecebidas.add(exchange.getRequestHeaders().getFirst("X-Fiscaladapter-Signature"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        servidorWebhook.start();

        String accessToken = obterAccessToken();
        String respostaWebhook = mockMvc.perform(put("/api/v1/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.fiscaladapter.webhook.WebhookUrlRequest(
                                "http://localhost:" + servidorWebhook.getAddress().getPort() + "/webhook")))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.secret").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        webhookSecret = objectMapper.readTree(respostaWebhook).get("secret").asText();
    }

    @AfterAll
    static void pararServidorWebhook() {
        if (servidorWebhook != null) {
            servidorWebhook.stop(0);
        }
    }

    @BeforeEach
    void prepararMocks() {
        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso da NF-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00", true));
    }

    @Test
    void deveEnfileirarProcessarENotificarWebhookQuandoAutorizada() throws Exception {
        String accessToken = obterAccessToken();

        String respostaEnfileiramento = mockMvc.perform(post("/api/v1/nfe/assincrono")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(500L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-assincrona-001"))
                .andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(respostaEnfileiramento).get("id").asLong();

        worker.processarPendentes();

        mockMvc.perform(get("/api/v1/nfe/assincrono/" + id)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CONCLUIDA"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultado.autorizada").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.resultado.numeroProtocolo").value("135260000000001"));

        String corpoWebhook = requisicoesRecebidas.poll(10, TimeUnit.SECONDS);
        assertThat(corpoWebhook).isNotNull().contains("\"tipo\":\"nfe.autorizada\"").contains("135260000000001")
                .contains("\"eventoId\"");
        String assinaturaRecebida = assinaturasRecebidas.poll(10, TimeUnit.SECONDS);
        assertThat(assinaturaRecebida).isEqualTo("sha256=" + assinarHmacEsperado(corpoWebhook));
    }

    private String assinarHmacEsperado(String corpo) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(mac.doFinal(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deveEnfileirarDeFormaIdempotentePorClientIdEIdempotencyKey() throws Exception {
        String accessToken = obterAccessToken();

        String primeiraResposta = mockMvc.perform(post("/api/v1/nfe/assincrono")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(501L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-assincrona-002"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String segundaResposta = mockMvc.perform(post("/api/v1/nfe/assincrono")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(501L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-assincrona-002"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        Long primeiroId = objectMapper.readTree(primeiraResposta).get("id").asLong();
        Long segundoId = objectMapper.readTree(segundaResposta).get("id").asLong();
        assertThat(segundoId).isEqualTo(primeiroId);
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

    private NfePedidoEmissaoRequest pedidoValido(long numeroNota) {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rua Teste", "100", null, "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitRequest emit = new EmitRequest(CNPJ_EMISSOR, null, "EMPRESA ASSINCRONA LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "3");

        EnderecoNfeRequest enderecoDestinatario = new EnderecoNfeRequest("Av. Cliente", "200", null, "Jardins", "3550308", "Sao Paulo", "SP", "02000000", "1058", "Brasil", null);
        DestRequest dest = new DestRequest(null, "98765432100", null, "CLIENTE TESTE", enderecoDestinatario, 9, null, null, null, "cliente@teste.com");

        Icms00Request icms00 = new Icms00Request("0", "00", 3, BigDecimal.valueOf(100.00), BigDecimal.valueOf(18.00), BigDecimal.valueOf(18.00));
        PisAliqRequest pisAliq = new PisAliqRequest("01", BigDecimal.valueOf(100.00), BigDecimal.valueOf(1.65), BigDecimal.valueOf(1.65));
        CofinsAliqRequest cofinsAliq = new CofinsAliqRequest("01", BigDecimal.valueOf(100.00), BigDecimal.valueOf(7.60), BigDecimal.valueOf(7.60));
        ImpostoRequest imposto = new ImpostoRequest(new IcmsRequest(icms00, null, null), null, new PisRequest(pisAliq), new CofinsRequest(cofinsAliq));

        ProdRequest prod = new ProdRequest("PROD001", "SEM GTIN", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE, BigDecimal.valueOf(100.00), BigDecimal.valueOf(100.00),
                "SEM GTIN", "UN", BigDecimal.ONE, BigDecimal.valueOf(100.00), 1);

        DetRequest det = new DetRequest(1, prod, imposto);

        IdeRequest ide = new IdeRequest(35, "VENDA DE MERCADORIA", 1, numeroNota, LocalDate.of(2026, 3, 15),
                1, 1, "3550308", 1, 1, 2, 1, 1, 9, 0, "1.0.0");

        TranspRequest transp = new TranspRequest(9);
        PagRequest pag = new PagRequest(List.of(new DetPagRequest("01", BigDecimal.valueOf(100.00))));

        InfNfeRequest infNFe = new InfNfeRequest(ide, emit, dest, List.of(det), transp, pag);

        return new NfePedidoEmissaoRequest("homologacao", "teste-assincrono", infNFe);
    }
}
