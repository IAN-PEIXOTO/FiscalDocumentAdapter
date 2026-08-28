package com.fiscaladapter.api.nfe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import com.fiscaladapter.sefaz.nfe.SefazEndpointRegistry;
import com.fiscaladapter.sefaz.nfe.TipoServicoSefaz;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de ponta a ponta (FIS-13): exercita o fluxo real e completo de emissao
 * - POST /api/v1/nfe -> mapeamento -> RVN -> geracao/assinatura/validacao XSD
 * do XML -> SOAP 1.2 com mTLS -> interpretacao da resposta -> DANFE - sem
 * nenhum mock de classe de dominio ou de servico da aplicacao. So dois pontos
 * sao substituidos: o endereco do webservice (SefazEndpointRegistry) e a
 * fabrica de HttpClient (SefazHttpClientFactory, para o cliente aceitar o
 * certificado autoassinado do servidor de teste) - ambos redirecionando para
 * um servidor SOAP local (ServidorSoapDeTeste), simulando a SEFAZ real sem
 * depender do ambiente de homologacao (inacessivel nesta sessao) nem de um
 * certificado ICP-Brasil genuino.
 *
 * Serve tambem como o "modo mock para desenvolvimento" pedido no epico: rodar
 * este teste (ou copiar o padrao) e a forma de validar o pipeline completo
 * localmente sem qualquer dependencia externa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfeEmissaoPontaAPontaTest {

    private static final String CNPJ_EMISSOR = "11444777000161";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClienteApiService clienteApiService;

    @Autowired
    private CertificadoEmissorService certificadoEmissorService;

    @MockBean
    private SefazEndpointRegistry endpointRegistry;

    @MockBean
    private SefazHttpClientFactory httpClientFactory;

    private String clientId;
    private String clientSecret;

    @BeforeAll
    void prepararClienteECertificado() throws Exception {
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente ponta a ponta");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    @Test
    void deveEmitirNfeDeVerdadeAtePorMTlsESoapContraServidorLocalDeTeste() throws Exception {
        String protocoloEsperado = "135260000000099";
        String respostaAutorizada =
                "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                        + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeAutorizacao4\">"
                        + "<retEnviNFe versao=\"4.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                        + "<tpAmb>2</tpAmb><verAplic>SP1.0</verAplic><cStat>103</cStat><xMotivo>Lote recebido com sucesso</xMotivo>"
                        + "<protNFe versao=\"4.00\"><infProt><tpAmb>2</tpAmb><cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>"
                        + "<nProt>" + protocoloEsperado + "</nProt><dhRecbto>2026-03-15T10:00:00-03:00</dhRecbto></infProt></protNFe>"
                        + "</retEnviNFe>"
                        + "</nfeResultMsg></soap:Body></soap:Envelope>";

        StringBuilder requisicaoCapturada = new StringBuilder();
        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            requisicaoCapturada.append(req);
            return respostaAutorizada;
        })) {
            when(endpointRegistry.obterUrl(eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), eq(TipoServicoSefaz.AUTORIZACAO)))
                    .thenReturn(servidor.url());
            when(httpClientFactory.criar(any()))
                    .thenAnswer(invocacao -> new SefazHttpClientFactory().criarComTrustManager(
                            invocacao.getArgument(0), servidor.trustManagerQueAceitaEsteServidor()));

            String accessToken = obterAccessToken();

            mockMvc.perform(post("/api/v1/nfe")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(pedidoValido()))
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Idempotency-Key", "chave-ponta-a-ponta-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.chaveAcesso").isNotEmpty())
                    .andExpect(jsonPath("$.autorizada").value(true))
                    .andExpect(jsonPath("$.numeroProtocolo").value(protocoloEsperado))
                    .andExpect(jsonPath("$.danfePdfBase64").isNotEmpty());

            // prova de que o XML que chegou ao servidor SOAP e o XML real gerado e assinado pela
            // aplicacao (nao um stub) - contem a assinatura digital e o CNPJ do emitente
            assertThat(requisicaoCapturada.toString())
                    .contains("enviNFe")
                    .contains("<indSinc>1</indSinc>")
                    .contains("Signature")
                    .contains(CNPJ_EMISSOR);
        }
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
        EmitRequest emit = new EmitRequest(CNPJ_EMISSOR, null, "EMPRESA PONTA A PONTA LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "3");

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

        IdeRequest ide = new IdeRequest(35, "VENDA DE MERCADORIA", 1, 42L, LocalDate.of(2026, 3, 15),
                1, 1, "3550308", 1, 1, 2, 1, 1, 9, 0, "1.0.0");

        TranspRequest transp = new TranspRequest(9);
        PagRequest pag = new PagRequest(List.of(new DetPagRequest("01", BigDecimal.valueOf(100.00))));

        InfNfeRequest infNFe = new InfNfeRequest(ide, emit, dest, List.of(det), transp, pag);

        return new NfePedidoEmissaoRequest("homologacao", "teste-ponta-a-ponta", infNFe);
    }
}
