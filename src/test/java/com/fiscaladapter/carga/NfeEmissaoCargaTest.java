package com.fiscaladapter.carga;

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
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import com.fiscaladapter.sefaz.nfe.SefazEndpointRegistry;
import com.fiscaladapter.sefaz.nfe.TipoServicoSefaz;
import com.fiscaladapter.seguranca.ClienteApiService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de carga (FIS-41): simula um pico de emissoes simultaneas de NFe
 * contra o pipeline real da aplicacao (mapeamento -> RVN -> assinatura XML ->
 * SOAP -> numeracao -> retencao), substituindo so o servidor SEFAZ por um
 * servidor SOAP local com uma latencia artificial (SIMULACAO_LATENCIA_SEFAZ)
 * para aproximar o tempo de rede de um ambiente de homologacao real, sem
 * depender dele (inacessivel nesta sessao). Mede latencia (p50/p95/p99),
 * throughput e taxa de erro, e imprime um relatorio no console - os
 * resultados de uma execucao real desta sessao estao documentados no README
 * (secao "Teste de carga e performance"), junto dos gargalos identificados.
 *
 * ATENCAO (limitacao conhecida, nao contornavel nesta sessao): roda contra
 * H2 em memoria (perfil dev) numa unica JVM local, nao contra Postgres real
 * nem infraestrutura de producao - os numeros absolutos daqui NAO devem ser
 * usados como capacidade real de producao, so como um sinal relativo de onde
 * o pipeline gasta tempo e onde a concorrencia degrada. Ver README para a
 * lista de gargalos registrados como debito tecnico (FIS-41).
 *
 * Excluido do `mvn verify` por padrao (@Tag("carga") + excludedGroups no
 * surefire) porque e mais lento e menos deterministico que os testes
 * unitarios/de integracao normais - rodar explicitamente com:
 * ./mvnw test -Dgroups=carga -Dtest=NfeEmissaoCargaTest
 */
@Tag("carga")
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NfeEmissaoCargaTest {

    private static final String CNPJ_EMISSOR = "11444777000161";
    private static final String CNPJ_EMISSOR_RATE_LIMIT = "22555888000142";
    private static final int TOTAL_REQUISICOES = 55;
    private static final int CONCORRENCIA = 20;
    private static final Duration SIMULACAO_LATENCIA_SEFAZ = Duration.ofMillis(50);

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
        ClienteApiService.CredenciaisGeradas credenciais = clienteApiService.cadastrar("Cliente de carga");
        this.clientId = credenciais.clientId();
        this.clientSecret = credenciais.clientSecret();

        byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR, "senha123".toCharArray(),
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        certificadoEmissorService.registrar(clientId, p12, "senha123".toCharArray());
    }

    /**
     * Pico "seguro" (dentro do limite de 60 req/min por client_id - ver
     * RateLimitFilter): mede a latencia real do pipeline completo sob
     * concorrencia, sem o rate limiter interferir. E aqui que aparecem os
     * gargalos de banco/CPU (ver README para a analise).
     */
    @Test
    void devePicoSeguroMedirLatenciaRealDoPipelineSobConcorrencia() throws Exception {
        try (ServidorSoapDeTeste servidor = iniciarServidorSefazSimulado()) {
            String accessToken = obterAccessToken();

            RelatorioDeCarga relatorio = executarCarga(accessToken, TOTAL_REQUISICOES, CONCORRENCIA, 9_000_000L, CNPJ_EMISSOR);
            relatorio.imprimir("pico seguro (" + TOTAL_REQUISICOES + " req, concorrencia " + CONCORRENCIA + ")");

            assertThat(relatorio.taxaDeErro()).isZero();
            assertThat(relatorio.totalConcluido()).isEqualTo(TOTAL_REQUISICOES);
        }
    }

    /**
     * Pico que ULTRAPASSA a capacidade configurada (RateLimitFilter, 60
     * req/min por client_id): prova que o limite e aplicado de fato sob
     * concorrencia real (nao so em testes sequenciais) - o gargalo mais
     * facil de atingir na pratica, antes de qualquer limite de banco/CPU.
     */
    @Test
    void devicoAcimaDoLimiteDeTaxaDeveSerRejeitadoComHttp429() throws Exception {
        try (ServidorSoapDeTeste servidor = iniciarServidorSefazSimulado()) {
            // client_id proprio (nao o da classe): o rate limit e por client_id e por janela de
            // 60s - reusar o client_id do teste "pico seguro" contaminaria a contagem com o
            // consumo de janela que ele ja fez, se os dois testes caírem no mesmo minuto.
            ClienteApiService.CredenciaisGeradas outroCliente = clienteApiService.cadastrar("Cliente de carga (rate limit)");
            byte[] p12 = TestCertificadoFactory.gerarP12(CNPJ_EMISSOR_RATE_LIMIT, "senha123".toCharArray(),
                    Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
            certificadoEmissorService.registrar(outroCliente.clientId(), p12, "senha123".toCharArray());
            String accessToken = obterAccessToken(outroCliente.clientId(), outroCliente.clientSecret());

            int totalAcimaDoLimite = 70;
            RelatorioDeCarga relatorio = executarCarga(accessToken, totalAcimaDoLimite, 20, 8_000_000L, CNPJ_EMISSOR_RATE_LIMIT);
            relatorio.imprimir("pico acima do limite (" + totalAcimaDoLimite + " req num unico minuto)");

            long totalRejeitadoPorLimite = relatorio.resultados().stream()
                    .filter(r -> r.statusHttp() == 429)
                    .count();

            // >=10: os primeiros ~60 (menos alguma variacao de timing entre o inicio da janela do
            // minuto e o disparo das requisicoes) passam, o resto e rejeitado - a contagem exata
            // depende de quando a janela de 60s do RateLimitFilter comecou.
            assertThat(totalRejeitadoPorLimite).isGreaterThanOrEqualTo(totalAcimaDoLimite - 61);
        }
    }

    private ServidorSoapDeTeste iniciarServidorSefazSimulado() throws Exception {
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

        ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            simularLatenciaDeRede();
            return respostaAutorizada;
        });
        when(endpointRegistry.obterUrl(eq("SP"), eq(TipoAmbiente.HOMOLOGACAO), eq(TipoServicoSefaz.AUTORIZACAO)))
                .thenReturn(servidor.url());
        when(httpClientFactory.criar(any()))
                .thenAnswer(invocacao -> new SefazHttpClientFactory().criarComTrustManager(
                        invocacao.getArgument(0), servidor.trustManagerQueAceitaEsteServidor()));
        return servidor;
    }

    private RelatorioDeCarga executarCarga(String accessToken, int totalRequisicoes, int concorrencia,
                                            long numeroNotaInicial, String cnpjEmissor) throws Exception {
        AtomicLong contadorNumeroNota = new AtomicLong(numeroNotaInicial);
        AtomicInteger contadorIdempotencia = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(concorrencia);

        List<Callable<ResultadoRequisicao>> tarefas = new ArrayList<>();
        for (int i = 0; i < totalRequisicoes; i++) {
            tarefas.add(() -> {
                long numeroNota = contadorNumeroNota.incrementAndGet();
                String idempotencyKey = "chave-carga-" + contadorIdempotencia.incrementAndGet();
                long inicio = System.nanoTime();
                try {
                    int status = mockMvc.perform(post("/api/v1/nfe")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(pedidoValido(numeroNota, cnpjEmissor)))
                                    .header("Authorization", "Bearer " + accessToken)
                                    .header("Idempotency-Key", idempotencyKey))
                            .andReturn().getResponse().getStatus();
                    long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
                    return new ResultadoRequisicao(duracaoMs, status);
                } catch (Exception e) {
                    long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
                    return new ResultadoRequisicao(duracaoMs, -1);
                }
            });
        }

        long inicioTotal = System.nanoTime();
        List<Future<ResultadoRequisicao>> futuros = executor.invokeAll(tarefas);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        Duration duracaoTotal = Duration.ofNanos(System.nanoTime() - inicioTotal);

        List<ResultadoRequisicao> resultados = new ArrayList<>();
        for (Future<ResultadoRequisicao> futuro : futuros) {
            resultados.add(futuro.get());
        }

        return new RelatorioDeCarga(resultados, duracaoTotal);
    }

    private void simularLatenciaDeRede() {
        try {
            Thread.sleep(SIMULACAO_LATENCIA_SEFAZ.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String obterAccessToken() throws Exception {
        return obterAccessToken(clientId, clientSecret);
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

    private NfePedidoEmissaoRequest pedidoValido(long numeroNota, String cnpjEmissor) {
        EnderecoNfeRequest enderecoEmitente = new EnderecoNfeRequest("Rua Teste", "100", null, "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1058", "Brasil", "1130000000");
        EmitRequest emit = new EmitRequest(cnpjEmissor, null, "EMPRESA CARGA LTDA", "TESTE", enderecoEmitente, "111222333", null, null, null, "3");

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

        return new NfePedidoEmissaoRequest("homologacao", "teste-carga", infNFe);
    }

    private record ResultadoRequisicao(long duracaoMs, int statusHttp) {
        boolean sucesso() {
            return statusHttp == 200;
        }
    }

    private record RelatorioDeCarga(List<ResultadoRequisicao> resultados, Duration duracaoTotal) {

        int totalConcluido() {
            return resultados.size();
        }

        double taxaDeErro() {
            long erros = resultados.stream().filter(r -> !r.sucesso()).count();
            return (double) erros / resultados.size();
        }

        void imprimir(String rotulo) {
            List<Long> latencias = resultados.stream().map(ResultadoRequisicao::duracaoMs).sorted().toList();
            long erros = resultados.stream().filter(r -> !r.sucesso()).count();
            long rejeitadosPorRateLimit = resultados.stream().filter(r -> r.statusHttp() == 429).count();
            double throughput = resultados.size() / (duracaoTotal.toMillis() / 1000.0);

            System.out.println("===== FIS-41: relatorio de teste de carga - " + rotulo + " =====");
            System.out.println("Total de requisicoes: " + resultados.size());
            System.out.println("Duracao total: " + duracaoTotal.toMillis() + " ms");
            System.out.println("Throughput: " + String.format("%.1f", throughput) + " emissoes/s");
            System.out.println("Taxa de erro: " + erros + "/" + resultados.size()
                    + " (rejeitados por rate limit HTTP 429: " + rejeitadosPorRateLimit + ")");
            System.out.println("Latencia min: " + latencias.get(0) + " ms");
            System.out.println("Latencia p50: " + percentil(latencias, 50) + " ms");
            System.out.println("Latencia p95: " + percentil(latencias, 95) + " ms");
            System.out.println("Latencia p99: " + percentil(latencias, 99) + " ms");
            System.out.println("Latencia max: " + latencias.get(latencias.size() - 1) + " ms");
            System.out.println("==================================================================");
        }

        private long percentil(List<Long> latenciasOrdenadas, int percentil) {
            int indice = (int) Math.ceil(percentil / 100.0 * latenciasOrdenadas.size()) - 1;
            return latenciasOrdenadas.get(Math.max(0, Math.min(indice, latenciasOrdenadas.size() - 1)));
        }
    }
}
