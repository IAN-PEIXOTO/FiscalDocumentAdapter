package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O payload usado aqui segue o mesmo formato da API ACBr
 * (https://dev.acbr.api.br/docs/api - schema NfePedidoEmissao), para que
 * sistemas ja integrados com ela troquem apenas a URL de destino. A
 * autenticacao segue o mesmo padrao tambem: OAuth2 client_credentials
 * (FIS-15).
 */
@SpringBootTest
@AutoConfigureMockMvc
class NfeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    private String clientId;
    private String clientSecret;

    @BeforeEach
    void criarClienteDeTeste() {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();
    }

    @Test
    void deveEmitirNfeComSucessoRetornandoChaveEXmlAssinado() throws Exception {
        String accessToken = obterAccessToken();

        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199", "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));

        MockMultipartFile documento = new MockMultipartFile(
                "documento", "documento.json", "application/json",
                objectMapper.writeValueAsBytes(pedidoValido()));
        MockMultipartFile certificado = new MockMultipartFile(
                "certificado", "certificado.p12", "application/x-pkcs12", p12);

        mockMvc.perform(multipart("/api/v1/nfe")
                        .file(documento)
                        .file(certificado)
                        .param("senhaCertificado", "senha123")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-teste-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                .andExpect(jsonPath("$.xmlAssinado").exists());
    }

    @Test
    void reenviarComMesmaChaveDeIdempotenciaDeveRetornarMesmaRespostaSemReprocessar() throws Exception {
        String accessToken = obterAccessToken();
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199", "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));

        String respostaPrimeiraChamada = mockMvc.perform(multipart("/api/v1/nfe")
                        .file(new MockMultipartFile("documento", "d.json", "application/json", objectMapper.writeValueAsBytes(pedidoValido())))
                        .file(new MockMultipartFile("certificado", "c.p12", "application/x-pkcs12", p12))
                        .param("senhaCertificado", "senha123")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-repetida"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // segunda tentativa com o MESMO numero de nota (nNF=42): se fosse reprocessada,
        // a numeracao sequencial atribuiria um numero diferente e a chave de acesso mudaria
        String respostaSegundaChamada = mockMvc.perform(multipart("/api/v1/nfe")
                        .file(new MockMultipartFile("documento", "d.json", "application/json", objectMapper.writeValueAsBytes(pedidoValido())))
                        .file(new MockMultipartFile("certificado", "c.p12", "application/x-pkcs12", p12))
                        .param("senhaCertificado", "senha123")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-repetida"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(respostaSegundaChamada).isEqualTo(respostaPrimeiraChamada);
    }

    @Test
    void deveRejeitarRequisicaoSemChaveDeIdempotencia() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(multipart("/api/v1/nfe")
                        .file(new MockMultipartFile("documento", "d.json", "application/json", objectMapper.writeValueAsBytes(pedidoValido())))
                        .file(new MockMultipartFile("certificado", "c.p12", "application/x-pkcs12", new byte[]{1, 2, 3}))
                        .param("senhaCertificado", "qualquer")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarRequisicaoSemToken() throws Exception {
        MockMultipartFile documento = new MockMultipartFile(
                "documento", "documento.json", "application/json",
                objectMapper.writeValueAsBytes(pedidoValido()));
        MockMultipartFile certificado = new MockMultipartFile(
                "certificado", "certificado.p12", "application/x-pkcs12", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/nfe")
                        .file(documento)
                        .file(certificado)
                        .param("senhaCertificado", "qualquer"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRejeitarDocumentoComCamposObrigatoriosFaltando() throws Exception {
        String accessToken = obterAccessToken();

        MockMultipartFile documentoInvalido = new MockMultipartFile(
                "documento", "documento.json", "application/json", "{}".getBytes());
        MockMultipartFile certificado = new MockMultipartFile(
                "certificado", "certificado.p12", "application/x-pkcs12", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/nfe")
                        .file(documentoInvalido)
                        .file(certificado)
                        .param("senhaCertificado", "qualquer")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-teste-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados invalidos no documento enviado"));
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

    private NfePedidoEmissaoRequest pedidoValido() {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rua Teste", "100", null, "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitRequest emit = new EmitRequest("12345678000199", null, "EMPRESA TESTE LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "1");

        EnderecoNfeRequest enderecoDestinatario = new EnderecoNfeRequest("Av. Cliente", "200", null, "Jardins", "3550308", "Sao Paulo", "SP", "02000000", "1058", "Brasil", null);
        DestRequest dest = new DestRequest(null, "98765432100", null, "CLIENTE TESTE", enderecoDestinatario, 9, null, null, null, "cliente@teste.com");

        Icms00Request icms00 = new Icms00Request("0", "00", 3, BigDecimal.valueOf(100.00), BigDecimal.valueOf(18.00), BigDecimal.valueOf(18.00));
        PisAliqRequest pisAliq = new PisAliqRequest("01", BigDecimal.valueOf(100.00), BigDecimal.valueOf(1.65), BigDecimal.valueOf(1.65));
        CofinsAliqRequest cofinsAliq = new CofinsAliqRequest("01", BigDecimal.valueOf(100.00), BigDecimal.valueOf(7.60), BigDecimal.valueOf(7.60));
        ImpostoRequest imposto = new ImpostoRequest(new IcmsRequest(icms00), null, new PisRequest(pisAliq), new CofinsRequest(cofinsAliq));

        ProdRequest prod = new ProdRequest("PROD001", "SEM GTIN", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE, BigDecimal.valueOf(100.00), BigDecimal.valueOf(100.00),
                "SEM GTIN", "UN", BigDecimal.ONE, BigDecimal.valueOf(100.00), 1);

        DetRequest det = new DetRequest(1, prod, imposto);

        IdeRequest ide = new IdeRequest(35, "VENDA DE MERCADORIA", 1, 42L, LocalDate.of(2026, 3, 15),
                1, 1, "3550308", 1, 1, 2, 1, 1, 9, 0, "1.0.0");

        TranspRequest transp = new TranspRequest(9);
        PagRequest pag = new PagRequest(List.of(new DetPagRequest("01", BigDecimal.valueOf(100.00))));

        InfNfeRequest infNFe = new InfNfeRequest(ide, emit, dest, List.of(det), transp, pag);

        return new NfePedidoEmissaoRequest("homologacao", "teste-001", infNFe);
    }
}
