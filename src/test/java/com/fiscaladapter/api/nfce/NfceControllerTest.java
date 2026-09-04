package com.fiscaladapter.api.nfce;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.CofinsAliqRequest;
import com.fiscaladapter.api.nfe.CofinsRequest;
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
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
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
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova de ponta a ponta da emissao de NFC-e via API (FIS-43): mesmo payload
 * da NFe, sem destinatario (consumidor nao identificado), com QR Code online
 * inserido antes da transmissao sincrona.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfceControllerTest {

    private static final String CNPJ_EMISSOR = "44888999000122";

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

    @SpyBean
    private RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente NFC-e de teste");
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
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso da NF-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00", true));
    }

    @Test
    void deveEmitirNfceSemDestinatarioComQrCodeEDanfe() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(500L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-nfce-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000001"))
                .andExpect(jsonPath("$.conteudoQrCode").isNotEmpty())
                .andExpect(jsonPath("$.urlConsultaPublica").value("https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode"))
                .andExpect(jsonPath("$.danfePdfBase64").isNotEmpty())
                .andExpect(jsonPath("$.xmlAssinado", org.hamcrest.Matchers.containsString("infNFeSupl")));
    }

    /**
     * FIS-100: mesmo comportamento de NfeControllerTest.deveReservarNumeroEArquivarDocumentoQuandoUsoEDenegado -
     * cStat 110 (uso denegado) consome definitivamente o numero na SEFAZ, entao precisa reservar e
     * arquivar mesmo sem autorizacao.
     */
    @Test
    void deveReservarNumeroEArquivarDocumentoQuandoUsoEDenegado() throws Exception {
        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("301", "Uso Denegado: Irregularidade fiscal do emitente", null, null, false));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(600L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-nfce-denegado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizada").value(false))
                .andExpect(jsonPath("$.codigoStatusSefaz").value("301"));

        verify(retencaoDocumentoFiscalService).arquivar(any(), any(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/nfce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(600L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-nfce-denegado-reenvio"))
                .andExpect(status().isConflict());
    }

    @Test
    void chaveDeAcessoDaNfceDeveTerModelo65() throws Exception {
        String accessToken = obterAccessToken();

        String resposta = mockMvc.perform(post("/api/v1/nfce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(501L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-nfce-002"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String chaveAcesso = objectMapper.readTree(resposta).get("chaveAcesso").asText();
        org.assertj.core.api.Assertions.assertThat(chaveAcesso.substring(20, 22)).isEqualTo("65");
    }

    @Test
    void mesmaIdempotencyKeyNaoDeveColidirEntreNfeENfce() throws Exception {
        String accessToken = obterAccessToken();
        String chaveIdempotencia = "chave-compartilhada-nfe-nfce";

        String respostaNfce = mockMvc.perform(post("/api/v1/nfce")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(502L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", chaveIdempotencia))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // mesma Idempotency-Key, mas para /api/v1/nfe - nao deveria colidir nem devolver a
        // resposta da NFC-e (tipos incompativeis) - ver IdempotenciaService/tipoOperacao, FIS-43
        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValidoComDestinatario(503L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", chaveIdempotencia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty());

        org.assertj.core.api.Assertions.assertThat(respostaNfce).contains("conteudoQrCode");
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
        EmitRequest emit = new EmitRequest(CNPJ_EMISSOR, null, "EMPRESA NFCE LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "3");

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

        PagRequest pag = new PagRequest(List.of(new DetPagRequest("01", BigDecimal.valueOf(100.00))));

        com.fiscaladapter.api.nfe.TranspRequest transp = new com.fiscaladapter.api.nfe.TranspRequest(9);
        InfNfeRequest infNFe = new InfNfeRequest(ide, emit, null, List.of(det), transp, pag);

        return new NfePedidoEmissaoRequest("homologacao", "teste-nfce", infNFe);
    }

    private NfePedidoEmissaoRequest pedidoValidoComDestinatario(long numeroNota) {
        NfePedidoEmissaoRequest semDest = pedidoValido(numeroNota);
        EnderecoNfeRequest enderecoDestinatario = new EnderecoNfeRequest("Av. Cliente", "200", null, "Jardins", "3550308", "Sao Paulo", "SP", "02000000", "1058", "Brasil", null);
        com.fiscaladapter.api.nfe.DestRequest dest = new com.fiscaladapter.api.nfe.DestRequest(
                null, "98765432100", null, "CLIENTE TESTE", enderecoDestinatario, 9, null, null, null, "cliente@teste.com");
        com.fiscaladapter.api.nfe.TranspRequest transp = new com.fiscaladapter.api.nfe.TranspRequest(9);
        InfNfeRequest infNFeComDest = new InfNfeRequest(semDest.infNFe().ide(), semDest.infNFe().emit(), dest,
                semDest.infNFe().det(), transp, semDest.infNFe().pag());
        return new NfePedidoEmissaoRequest(semDest.ambiente(), semDest.referencia(), infNFeComDest);
    }
}
