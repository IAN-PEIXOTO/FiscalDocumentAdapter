package com.fiscaladapter.api.mdfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.sefaz.mdfe.EncerramentoResponse;
import com.fiscaladapter.sefaz.mdfe.MdfeAutorizacaoClient;
import com.fiscaladapter.sefaz.mdfe.MdfeCancelamentoClient;
import com.fiscaladapter.sefaz.mdfe.MdfeConsultaProtocoloClient;
import com.fiscaladapter.sefaz.mdfe.MdfeEncerramentoClient;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
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
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Prova a consulta, o encerramento (fim de percurso) e o prazo legal de cancelamento do MDF-e (FIS-45). */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MdfeConsultaControllerTest {

    private static final String CNPJ_EMISSOR = "88999000111122";

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

    @MockBean
    private MdfeConsultaProtocoloClient consultaProtocoloClient;

    @MockBean
    private MdfeCancelamentoClient cancelamentoClient;

    @MockBean
    private MdfeEncerramentoClient encerramentoClient;

    private String clientId;
    private String clientSecret;
    private String chaveAcessoMdfeEmitido;

    @BeforeAll
    void prepararCenario() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente MDF-e consulta de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());

        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso do MDF-e", "935260000000001",
                        "2026-03-15T10:00:00-03:00", true));

        String accessToken = obterAccessToken();
        String respostaEmissao = mockMvc.perform(post("/api/v1/mdfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(950L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-mdfe-consulta-setup"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        this.chaveAcessoMdfeEmitido = objectMapper.readTree(respostaEmissao).get("chaveAcesso").asText();
    }

    @Test
    void deveConsultarMdfeERetornarSituacao() throws Exception {
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do MDF-e", "935260000000001",
                        "2026-03-15T10:00:00-03:00"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeEmitido + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.encerrado").value(false));
    }

    @Test
    void deveRetornarDocumentosVinculadosNaConsulta() throws Exception {
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do MDF-e", "935260000000001",
                        "2026-03-15T10:00:00-03:00"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeEmitido + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chavesCteTransportados[0]").value("35260112345678000199570010000000421000000012"))
                .andExpect(jsonPath("$.chavesNfeTransportadas[0]").value("35260112345678000199550010000000421000000019"));
    }

    /** Emite um MDF-e proprio (nao o compartilhado por @BeforeAll) para nao afetar os testes de cancelamento com o estado "encerrado". */
    @Test
    void deveEncerrarOManifestoEBloquearCancelamentoDepois() throws Exception {
        String accessToken = obterAccessToken();
        String respostaEmissao = mockMvc.perform(post("/api/v1/mdfe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(951L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-mdfe-encerramento-setup"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String chaveAcessoMdfeParaEncerrar = objectMapper.readTree(respostaEmissao).get("chaveAcesso").asText();

        when(encerramentoClient.encerrar(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(EncerramentoResponse.de("135", "Evento registrado e vinculado ao MDF-e"));
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do MDF-e", "935260000000001",
                        "2026-03-15T10:00:00-03:00"));

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeParaEncerrar + "/encerramento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "935260000000001")
                        .param("codigoMunicipioEncerramento", "3304557")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrado").value(true))
                .andExpect(jsonPath("$.damdfePdfBase64").isNotEmpty());

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeParaEncerrar + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encerrado").value(true));

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeParaEncerrar + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "935260000000001")
                        .param("justificativa", "Tentativa de cancelamento apos encerramento")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("MDF-e ja encerrado")));
    }

    @Test
    void deveCancelarMdfeDentroDoPrazoDe24Horas() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do MDF-e", "935260000000001", dhRecbto));
        when(cancelamentoClient.cancelar(any(), any(), any(), any(), any(), any()))
                .thenReturn(CancelamentoResponse.de("135", "Evento registrado e vinculado ao MDF-e"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "935260000000001")
                        .param("justificativa", "Erro no cadastro do veiculo de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelado").value(true));
    }

    /** FIS-58: numeroProtocolo e concatenado direto no XML do evento - deve ser rejeitado cedo se nao for numerico. */
    @Test
    void deveRejeitarCancelamentoComNumeroProtocoloNaoNumerico() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "935</nProt><Injetado>x")
                        .param("justificativa", "Erro no cadastro do veiculo de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("numeroProtocolo")));
    }

    /** FIS-58: codigoMunicipioEncerramento e concatenado direto no XML do evento - deve ser rejeitado cedo se nao for numerico. */
    @Test
    void deveRejeitarEncerramentoComCodigoMunicipioNaoNumerico() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeEmitido + "/encerramento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "935260000000001")
                        .param("codigoMunicipioEncerramento", "3304557</cMun><Injetado>x")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("codigoMunicipioEncerramento")));
    }

    @Test
    void deveRejeitarCancelamentoDeMdfeForaDoPrazoDe24Horas() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusHours(26).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do MDF-e", "935260000000001", dhRecbto));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/mdfe/" + chaveAcessoMdfeEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "935260000000001")
                        .param("justificativa", "Erro no cadastro do veiculo de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("Prazo de cancelamento do MDF-e expirado")));
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

        return new MdfePedidoEmissaoRequest("homologacao", "teste-mdfe-consulta", infMDFe);
    }
}
