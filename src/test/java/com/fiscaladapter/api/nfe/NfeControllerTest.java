package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.nfe.NfeAutorizacaoClient;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * O payload usado aqui segue o mesmo formato da API ACBr
 * (https://dev.acbr.api.br/docs/api - schema NfePedidoEmissao), para que
 * sistemas ja integrados com ela troquem apenas a URL de destino. A
 * autenticacao segue o mesmo padrao tambem: OAuth2 client_credentials
 * (FIS-15). O certificado do emissor e resolvido pelo CNPJ do payload a
 * partir do que foi registrado via POST /api/v1/certificados (FIS-2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class NfeControllerTest {

    private static final String CNPJ_EMISSOR = "12345678000199";

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

    @BeforeEach
    void prepararCenario() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(p12, "senha123".toCharArray());

        // a comunicacao real com a SEFAZ e testada separadamente (com.fiscaladapter.sefaz.nfe.*Test);
        // aqui simulamos uma autorizacao bem-sucedida para testar so a orquestracao do controller
        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso da NF-e", "135260000000001", true));
    }

    @Test
    void deveEmitirNfeComSucessoRetornandoChaveEXmlAssinado() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido()))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-teste-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                .andExpect(jsonPath("$.xmlAssinado").exists())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.numeroProtocolo").value("135260000000001"));
    }

    @Test
    void deveRetornarRespostaComRejeicaoQuandoSefazRecusaODocumento() throws Exception {
        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("539", "Duplicidade de NF-e", null, false));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido()))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-rejeicao"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizada").value(false))
                .andExpect(jsonPath("$.codigoStatusSefaz").value("539"));
    }

    @Test
    void deveRetornarBadGatewayQuandoFalhaComunicacaoComSefaz() throws Exception {
        // endpoint normal e a contingencia (SVC-AN, mapeada para SP) precisam falhar os dois
        // para o erro se propagar - ver EmissaoNfeOrquestrador (FIS-7/FIS-37)
        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenThrow(new com.fiscaladapter.sefaz.SefazComunicacaoException("timeout de conexao"));
        when(autorizacaoClient.autorizar(any(), any(), any(), any(), any()))
                .thenThrow(new com.fiscaladapter.sefaz.SefazComunicacaoException("timeout na contingencia"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido()))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-falha-comunicacao"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void reenviarComMesmaChaveDeIdempotenciaDeveRetornarMesmaRespostaSemReprocessar() throws Exception {
        String accessToken = obterAccessToken();

        String respostaPrimeiraChamada = mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido()))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-repetida"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // segunda tentativa com o MESMO numero de nota (nNF=42): se fosse reprocessada,
        // a numeracao sequencial atribuiria um numero diferente e a chave de acesso mudaria
        String respostaSegundaChamada = mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido()))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-repetida"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(respostaSegundaChamada).isEqualTo(respostaPrimeiraChamada);
    }

    @Test
    void deveRejeitarRequisicaoSemChaveDeIdempotencia() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido()))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRejeitarRequisicaoSemToken() throws Exception {
        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRejeitarDocumentoComCamposObrigatoriosFaltando() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-teste-invalido"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados invalidos no documento enviado"));
    }

    @Test
    void deveRetornarNotFoundQuandoEmissorNaoTemCertificadoRegistrado() throws Exception {
        String accessToken = obterAccessToken();

        NfePedidoEmissaoRequest pedidoSemCertificado = pedidoValido();
        EmitRequest emitSemCertificado = new EmitRequest("00000000000000", null, "EMPRESA SEM CERTIFICADO", "TESTE",
                pedidoSemCertificado.infNFe().emit().enderEmit(), "111222333", null, null, null, "1");
        InfNfeRequest infNFe = new InfNfeRequest(pedidoSemCertificado.infNFe().ide(), emitSemCertificado,
                pedidoSemCertificado.infNFe().dest(), pedidoSemCertificado.infNFe().det(),
                pedidoSemCertificado.infNFe().transp(), pedidoSemCertificado.infNFe().pag());
        NfePedidoEmissaoRequest pedido = new NfePedidoEmissaoRequest("homologacao", "teste-002", infNFe);

        mockMvc.perform(post("/api/v1/nfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedido))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-sem-certificado"))
                .andExpect(status().isNotFound());
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
        EmitRequest emit = new EmitRequest(CNPJ_EMISSOR, null, "EMPRESA TESTE LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "1");

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
