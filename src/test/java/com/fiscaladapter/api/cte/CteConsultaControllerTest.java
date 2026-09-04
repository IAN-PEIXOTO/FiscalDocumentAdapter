package com.fiscaladapter.api.cte;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.api.nfe.EmitRequest;
import com.fiscaladapter.api.nfe.EnderecoNfeRequest;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.cte.TipoTomadorServico;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import com.fiscaladapter.sefaz.cte.CteAutorizacaoClient;
import com.fiscaladapter.sefaz.cte.CteCancelamentoClient;
import com.fiscaladapter.sefaz.cte.CteConsultaProtocoloClient;
import com.fiscaladapter.sefaz.nfe.AutorizacaoResponse;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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

/** Prova a consulta com vinculo das NF-e transportadas e o prazo legal de cancelamento do CT-e (FIS-44). */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CteConsultaControllerTest {

    private static final String CNPJ_EMISSOR = "66777888000133";
    private static final String CHAVE_NFE_TRANSPORTADA = "35260112345678000199550010000000421000000019";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @Autowired
    private RetencaoDocumentoFiscalService retencaoDocumentoFiscalService;

    @MockBean
    private CteAutorizacaoClient autorizacaoClient;

    @MockBean
    private CteConsultaProtocoloClient consultaProtocoloClient;

    @MockBean
    private CteCancelamentoClient cancelamentoClient;

    private String clientId;
    private String clientSecret;
    private String chaveAcessoCteEmitido;

    @BeforeAll
    void prepararCenario() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente CT-e consulta de teste");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());

        when(autorizacaoClient.autorizar(any(), any(), any(), any()))
                .thenReturn(new AutorizacaoResponse("100", "Autorizado o uso do CT-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00", true));

        String accessToken = obterAccessToken();
        String respostaEmissao = mockMvc.perform(post("/api/v1/cte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoValido(800L)))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "chave-cte-consulta-setup"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        this.chaveAcessoCteEmitido = objectMapper.readTree(respostaEmissao).get("chaveAcesso").asText();
    }

    @Test
    @Order(1)
    void deveConsultarCteERetornarNotasFiscaisTransportadas() throws Exception {
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do CT-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte/" + chaveAcessoCteEmitido + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizada").value(true))
                .andExpect(jsonPath("$.notasFiscaisTransportadas[0]").value(CHAVE_NFE_TRANSPORTADA))
                .andExpect(jsonPath("$.mdfeVinculado").doesNotExist());
    }

    @Test
    @Order(4)
    void deveRetornarMdfeVinculadoQuandoCteJaFoiManifestado() throws Exception {
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do CT-e", "135260000000001",
                        "2026-03-15T10:00:00-03:00"));

        String chaveMdfe = arquivarMdfeQueTransportaOCte();
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte/" + chaveAcessoCteEmitido + "/consulta")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mdfeVinculado").value(chaveMdfe));
    }

    @Test
    @Order(5)
    void deveBloquearCancelamentoDeCteJaManifestadoEmMdfe() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do CT-e", "135260000000001", dhRecbto));

        arquivarMdfeQueTransportaOCte();
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte/" + chaveAcessoCteEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Erro na contratacao do servico de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("ja manifestado no MDF-e")));
    }

    private String arquivarMdfeQueTransportaOCte() {
        String chaveMdfe = "35260" + CNPJ_EMISSOR + "580010000000900" + "1" + "23456789";
        String xmlMdfeFicticio = "<MDFe><infMDFe><infDoc><infMunDescarga>"
                + "<infCTe><chCTe>" + chaveAcessoCteEmitido + "</chCTe></infCTe>"
                + "</infMunDescarga></infDoc></infMDFe></MDFe>";
        retencaoDocumentoFiscalService.arquivar(chaveMdfe, CNPJ_EMISSOR, TipoDocumentoFiscal.MDFE,
                "935260000000001", xmlMdfeFicticio, LocalDate.of(2026, 3, 16));
        return chaveMdfe;
    }

    @Test
    @Order(2)
    void deveCancelarCteDentroDoPrazoDe168Horas() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do CT-e", "135260000000001", dhRecbto));
        when(cancelamentoClient.cancelar(any(), any(), any(), any(), any(), any()))
                .thenReturn(CancelamentoResponse.de("135", "Evento registrado e vinculado ao CT-e"));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte/" + chaveAcessoCteEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Erro na contratacao do servico de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelado").value(true));
    }

    /** FIS-58: numeroProtocolo e concatenado direto no XML do evento - deve ser rejeitado cedo se nao for numerico. */
    @Test
    @Order(6)
    void deveRejeitarCancelamentoComNumeroProtocoloNaoNumerico() throws Exception {
        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte/" + chaveAcessoCteEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135</nProt><Injetado>x")
                        .param("justificativa", "Erro na contratacao do servico de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("numeroProtocolo")));
    }

    @Test
    @Order(3)
    void deveRejeitarCancelamentoDeCteForaDoPrazoDe168Horas() throws Exception {
        String dhRecbto = OffsetDateTime.now().minusHours(170).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        when(consultaProtocoloClient.consultar(any(), any(), any(), any()))
                .thenReturn(ConsultaProtocoloResponse.de("100", "Autorizado o uso do CT-e", "135260000000001", dhRecbto));

        String accessToken = obterAccessToken();

        mockMvc.perform(post("/api/v1/cte/" + chaveAcessoCteEmitido + "/cancelamento")
                        .param("uf", "SP")
                        .param("ambiente", "HOMOLOGACAO")
                        .param("numeroProtocolo", "135260000000001")
                        .param("justificativa", "Erro na contratacao do servico de transporte")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("Prazo de cancelamento do CT-e expirado")));
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
                List.of(new NotaFiscalTransportadaRequest(CHAVE_NFE_TRANSPORTADA)),
                "12345678");

        return new CtePedidoEmissaoRequest("homologacao", "teste-cte-consulta", infCte);
    }
}
