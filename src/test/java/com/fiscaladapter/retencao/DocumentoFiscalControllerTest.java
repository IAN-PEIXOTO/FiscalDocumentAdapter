package com.fiscaladapter.retencao;

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

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova a retencao legal (FIS-26/34): apos autorizada, a NFe fica arquivada e pode ser recuperada so pelo dono do CNPJ emissor. */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentoFiscalControllerTest {

    private static final String CNPJ_EMISSOR = "33222111000181";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private NfeAutorizacaoClient autorizacaoClient;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente de retencao de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    @BeforeEach
    void prepararMocks() {
        when(autorizacaoClient.autorizar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso da NF-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00", true));
    }

    @Test
    void deveArquivarEPermitirRecuperarOXmlDaNfeAutorizadaPeloDono() throws Exception {
        String accessToken = obterAccessToken(clientId, clientSecret);

        String respostaEmissao = mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(700L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-retencao-001"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chaveAcesso = objectMapper.readTree(respostaEmissao).get("chaveAcesso").asText();
        String xmlOriginal = objectMapper.readTree(respostaEmissao).get("xmlAssinado").asText();

        mockMvc.perform(get("/api/v1/documentos/" + chaveAcesso)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").value(chaveAcesso))
                .andExpect(jsonPath("$.tipoDocumento").value("NFE"))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000001"))
                .andExpect(jsonPath("$.xmlAssinado").value(xmlOriginal));
    }

    @Test
    void deveRetornarNotFoundParaChaveDeAcessoInexistente() throws Exception {
        String accessToken = obterAccessToken(clientId, clientSecret);

        mockMvc.perform(get("/api/v1/documentos/00000000000000000000000000000000000000000000")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornarForbiddenQuandoOutroClienteTentaRecuperarDocumentoDeOutroEmissor() throws Exception {
        String accessToken = obterAccessToken(clientId, clientSecret);

        String respostaEmissao = mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(701L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-retencao-002"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String chaveAcesso = objectMapper.readTree(respostaEmissao).get("chaveAcesso").asText();

        ClienteApiService.CredenciaisGeradas outroCliente = clienteApiService.cadastrar("Outro tenant de retencao");
        String outroAccessToken = obterAccessToken(outroCliente.clientId(), outroCliente.clientSecret());

        mockMvc.perform(get("/api/v1/documentos/" + chaveAcesso)
                        .header("Authorization", "Bearer " + outroAccessToken))
                .andExpect(status().isForbidden());
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

    private NfePedidoEmissaoRequest pedidoValido(long numeroNota) {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rua Teste", "100", null, "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitRequest emit = new EmitRequest(CNPJ_EMISSOR, null, "EMPRESA RETENCAO LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "3");

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

        return new NfePedidoEmissaoRequest("homologacao", "teste-retencao", infNFe);
    }
}
