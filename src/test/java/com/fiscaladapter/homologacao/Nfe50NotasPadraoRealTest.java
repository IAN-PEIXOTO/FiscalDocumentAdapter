package com.fiscaladapter.homologacao;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.DetalhePagamento;
import com.fiscaladapter.documento.nfe.Destinatario;
import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.IdentificacaoNfe;
import com.fiscaladapter.documento.nfe.ImpostoItem;
import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.observabilidade.NfeEmissaoMetrics;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.nfe.EmissaoNfeOrquestrador;
import com.fiscaladapter.sefaz.nfe.NfeAutorizacaoClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
import com.fiscaladapter.sefaz.nfe.NfeEpecClient;
import com.fiscaladapter.sefaz.nfe.ResultadoEmissaoNfe;
import com.fiscaladapter.sefaz.nfe.SefazEndpointRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste MANUAL de emissao de 50 NF-e DE VERDADE contra a SEFAZ-PR de homologacao, ciclando
 * pelos 7 codigos de CST/ICMS00-40 e CSOSN/ICMSSN102 que este adapter de fato suporta hoje
 * (ver docs/manual-cst.md secao 2 - as combinacoes de CST/CSOSN mais comuns na pratica de
 * varejo/Simples Nacional: tributacao integral, isenta, nao tributada, suspensao, e as
 * quatro variantes sem credito do Simples). Usa exatamente o mesmo EmissaoNfeOrquestrador
 * real que a API usa em producao (mesmo padrao de SefazPrEmissaoNfeRealTest), sem nenhum
 * mock - o objetivo nao e taxa de autorizacao (que depende do cadastro real do CNPJ do
 * certificado bater com o CRT/CST declarado em cada nota, o que este teste nao controla),
 * e sim provar - de forma repetida e variada - que o pipeline completo (mTLS, assinatura,
 * validacao de schema, interpretacao de resposta) esta realmente se comunicando com a
 * SEFAZ, nota a nota, sem excecao de transporte/parsing.
 *
 * Efeito colateral real: consome 50 numeros de serie/documento no ambiente de HOMOLOGACAO
 * da SEFAZ-PR (sem valor fiscal, mas os numeros ficam registrados la) - a serie usada
 * (999) e exclusiva deste teste para nao colidir com nNF de outras execucoes manuais
 * (ex.: SefazPrEmissaoNfeRealTest, que nao define serie explicita).
 *
 * Sequencial, com pequena pausa entre notas (evita rajada contra o ambiente de
 * homologacao, que pode aplicar throttling nao documentado publicamente - diferente do
 * limite de "consulta de destinadas" que E documentado, aqui e so cautela).
 *
 * Excluido do `mvn verify` por padrao (@Tag("homologacao-real") + excludedGroups no
 * pom.xml) - mesmo mecanismo de SefazPrConectividadeRealTest/SefazPrEmissaoNfeRealTest
 * (ver o javadoc deles para como fornecer o certificado). Rodar explicitamente com:
 * FISCALADAPTER_TESTE_CERT_CAMINHO=... FISCALADAPTER_TESTE_CERT_SENHA=... \
 *   ./mvnw test -Dgroups=homologacao-real -Dtest=Nfe50NotasPadraoRealTest
 */
@Tag("homologacao-real")
@EnabledIfEnvironmentVariable(named = "FISCALADAPTER_TESTE_CERT_CAMINHO", matches = ".+")
class Nfe50NotasPadraoRealTest {

    private static final String UF = "PR";
    private static final String CODIGO_MUNICIPIO_CURITIBA = "4106902";
    private static final int SERIE_DESTE_TESTE = 999;
    private static final int TOTAL_NOTAS = 50;
    private static final long PAUSA_ENTRE_NOTAS_MS = 1500;

    /** Os 7 CST/CSOSN que NfeXmlGenerator escreve corretamente hoje (docs/manual-cst.md secao 2). */
    private static final List<CombinacaoCstCsosn> COMBINACOES_SUPORTADAS = List.of(
            new CombinacaoCstCsosn("ICMS00", "00", "3", "Tributacao integral"),
            new CombinacaoCstCsosn("ICMS40", "40", "3", "Isenta"),
            new CombinacaoCstCsosn("ICMS40", "41", "3", "Nao tributada"),
            new CombinacaoCstCsosn("ICMS40", "50", "3", "Suspensao"),
            new CombinacaoCstCsosn("ICMSSN102", "102", "1", "Simples Nacional sem credito"),
            new CombinacaoCstCsosn("ICMSSN102", "103", "1", "Isencao por faixa de receita"),
            new CombinacaoCstCsosn("ICMSSN102", "300", "1", "Imune"),
            new CombinacaoCstCsosn("ICMSSN102", "400", "1", "Nao tributada (Simples Nacional)")
    );

    @Test
    void deveEmitirCinquentaNotasComOsCstCsosnMaisUsadosEReceberRespostasReaisDaSefaz() throws Exception {
        CertificadoCarregado certificado = carregarCertificadoDoAmbiente();
        String cnpjEmitente = certificado.info().cnpj();
        assertThat(cnpjEmitente).as("o certificado precisa ter um CNPJ ICP-Brasil valido para emitir").isNotBlank();

        EmissaoNfeOrquestrador orquestrador = montarOrquestradorReal();
        long numeroBase = System.currentTimeMillis() / 1000 % 90_000_000L;

        List<ResultadoNota> resultados = new ArrayList<>();
        for (int i = 0; i < TOTAL_NOTAS; i++) {
            CombinacaoCstCsosn combinacao = COMBINACOES_SUPORTADAS.get(i % COMBINACOES_SUPORTADAS.size());
            long numero = numeroBase + i;
            NotaFiscalEletronica nfe = montarNfeDeTeste(cnpjEmitente, numero, combinacao, i);

            try {
                ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);
                resultados.add(ResultadoNota.sucesso(i, combinacao, resultado));
            } catch (Exception e) {
                resultados.add(ResultadoNota.falha(i, combinacao, e));
            }

            if (i < TOTAL_NOTAS - 1) {
                Thread.sleep(PAUSA_ENTRE_NOTAS_MS);
            }
        }

        imprimirRelatorio(resultados);

        long comRespostaEstruturada = resultados.stream().filter(ResultadoNota::respondeuComEstrutura).count();
        long comExcecaoDeTransporte = resultados.stream().filter(r -> !r.respondeuComEstrutura()).count();

        // O que prova comunicacao real com a SEFAZ e toda nota ter recebido uma resposta
        // estruturada (cStat/xMotivo), nao necessariamente cStat=100 (autorizada) - uma
        // rejeicao de negocio (CRT incompativel com o cadastro real, por exemplo) ainda e
        // prova de comunicacao; uma excecao de transporte/parsing, nao.
        assertThat(comExcecaoDeTransporte)
                .as("notas que nao receberam nenhuma resposta estruturada da SEFAZ (falha de comunicacao real): %s",
                        comExcecaoDeTransporte)
                .isZero();
        assertThat(comRespostaEstruturada).isEqualTo(TOTAL_NOTAS);
    }

    private NotaFiscalEletronica montarNfeDeTeste(String cnpjEmitente, long numero,
                                                    CombinacaoCstCsosn combinacao, int indice) {
        Endereco enderecoEmitente = new Endereco("Rua de Teste", "100", "Centro",
                CODIGO_MUNICIPIO_CURITIBA, "Curitiba", UF, "80000000", "4130000000");
        Emitente emitente = new Emitente(cnpjEmitente, "EMPRESA DE TESTE - HOMOLOGACAO", "TESTE",
                "1234567890", combinacao.crt(), enderecoEmitente);

        Endereco enderecoDestinatario = new Endereco("Avenida de Teste", "200", "Centro",
                CODIGO_MUNICIPIO_CURITIBA, "Curitiba", UF, "80000000", null);
        Destinatario destinatario = new Destinatario("11144477735", "CLIENTE DE TESTE - HOMOLOGACAO",
                "9", null, null, enderecoDestinatario);

        BigDecimal valorUnitario = BigDecimal.valueOf(10.00 + indice).setScale(2);
        boolean tributacaoIntegral = combinacao.grupoIcms().equals("ICMS00");
        BigDecimal vIcms = tributacaoIntegral ? valorUnitario.multiply(BigDecimal.valueOf(0.18)).setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);

        ImpostoItem imposto = new ImpostoItem(combinacao.grupoIcms(), "0", combinacao.codigo(),
                tributacaoIntegral ? valorUnitario : BigDecimal.ZERO.setScale(2),
                tributacaoIntegral ? BigDecimal.valueOf(18.00) : BigDecimal.ZERO.setScale(2),
                vIcms, BigDecimal.ZERO.setScale(2),
                valorUnitario.multiply(BigDecimal.valueOf(0.0165)).setScale(2, java.math.RoundingMode.HALF_UP),
                valorUnitario.multiply(BigDecimal.valueOf(0.076)).setScale(2, java.math.RoundingMode.HALF_UP));

        ItemNota item = new ItemNota(1, "TESTE" + String.format("%03d", indice),
                "PRODUTO DE TESTE - " + combinacao.descricao(), "61099010", "5102", "UN",
                BigDecimal.ONE.setScale(4), valorUnitario, valorUnitario, imposto);

        IdentificacaoNfe ide = new IdentificacaoNfe(UF, "VENDA DE MERCADORIA", SERIE_DESTE_TESTE, numero,
                LocalDate.now(ZoneId.of("America/Sao_Paulo")), TipoAmbiente.HOMOLOGACAO, 1, true,
                CODIGO_MUNICIPIO_CURITIBA, TipoDocumentoFiscal.NFE);

        DetalhePagamento pagamento = new DetalhePagamento("01", valorUnitario);

        return new NotaFiscalEletronica(ide, emitente, destinatario, List.of(item), List.of(pagamento));
    }

    private void imprimirRelatorio(List<ResultadoNota> resultados) {
        System.out.println("===== 50 notas padrao (CST/CSOSN mais usados) contra SEFAZ-" + UF + " homologacao =====");
        for (ResultadoNota r : resultados) {
            System.out.println(r.linhaDeRelatorio());
        }
        long autorizadas = resultados.stream().filter(ResultadoNota::autorizada).count();
        long viaEpec = resultados.stream().filter(ResultadoNota::viaEpec).count();
        long rejeitadas = resultados.stream().filter(r -> r.respondeuComEstrutura() && !r.autorizada() && !r.viaEpec()).count();
        long semResposta = resultados.stream().filter(r -> !r.respondeuComEstrutura()).count();
        System.out.println("Resumo: autorizadas=" + autorizadas + " viaEpec=" + viaEpec
                + " rejeitadas=" + rejeitadas + " semRespostaEstruturada(falha de comunicacao)=" + semResposta
                + " total=" + resultados.size());
        System.out.println("========================================================================");
    }

    private EmissaoNfeOrquestrador montarOrquestradorReal() {
        ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
        NfeXmlGenerator xmlGenerator = new NfeXmlGenerator(chaveAcessoService);
        AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();
        NfeXsdValidator xsdValidator = new NfeXsdValidator();
        SefazEndpointRegistry endpointRegistry = new SefazEndpointRegistry();
        SefazHttpClientFactory httpClientFactory = new SefazHttpClientFactory();
        NfeAutorizacaoClient autorizacaoClient = new NfeAutorizacaoClient(endpointRegistry, httpClientFactory);
        NfeConsultaProtocoloClient consultaProtocoloClient = new NfeConsultaProtocoloClient(endpointRegistry, httpClientFactory);
        NfeEpecClient epecClient = new NfeEpecClient(endpointRegistry, httpClientFactory, assinaturaXmlService);
        NfeEmissaoMetrics metrics = new NfeEmissaoMetrics(new SimpleMeterRegistry());

        return new EmissaoNfeOrquestrador(chaveAcessoService, xmlGenerator, assinaturaXmlService, xsdValidator,
                autorizacaoClient, consultaProtocoloClient, epecClient, metrics);
    }

    private CertificadoCarregado carregarCertificadoDoAmbiente() throws Exception {
        String caminho = System.getenv("FISCALADAPTER_TESTE_CERT_CAMINHO");
        String senha = System.getenv("FISCALADAPTER_TESTE_CERT_SENHA");
        if (senha == null || senha.isBlank()) {
            throw new IllegalStateException(
                    "Defina FISCALADAPTER_TESTE_CERT_SENHA (senha do certificado) antes de rodar este teste.");
        }
        try (InputStream arquivo = new FileInputStream(caminho)) {
            return new CertificadoDigitalService().carregar(arquivo, senha.toCharArray());
        }
    }

    private record CombinacaoCstCsosn(String grupoIcms, String codigo, String crt, String descricao) {
    }

    private record ResultadoNota(int indice, CombinacaoCstCsosn combinacao, ResultadoEmissaoNfe resultado, Exception erro) {

        static ResultadoNota sucesso(int indice, CombinacaoCstCsosn combinacao, ResultadoEmissaoNfe resultado) {
            return new ResultadoNota(indice, combinacao, resultado, null);
        }

        static ResultadoNota falha(int indice, CombinacaoCstCsosn combinacao, Exception erro) {
            return new ResultadoNota(indice, combinacao, null, erro);
        }

        boolean respondeuComEstrutura() {
            return resultado != null && resultado.autorizacao().codigoStatus() != null
                    && !resultado.autorizacao().codigoStatus().isBlank();
        }

        boolean autorizada() {
            return resultado != null && resultado.autorizacao().autorizada();
        }

        boolean viaEpec() {
            return resultado != null && resultado.viaEpec();
        }

        String linhaDeRelatorio() {
            String cst = (combinacao.grupoIcms().startsWith("ICMSSN") ? "CSOSN " : "CST ") + combinacao.codigo();
            if (resultado != null) {
                return String.format("nota %02d [%s - %s]: cStat=%s motivo=\"%s\" autorizada=%s viaEpec=%s chave=%s",
                        indice, cst, combinacao.descricao(), resultado.autorizacao().codigoStatus(),
                        resultado.autorizacao().motivo(), resultado.autorizacao().autorizada(), resultado.viaEpec(),
                        resultado.chaveAcesso());
            }
            return String.format("nota %02d [%s - %s]: FALHA DE COMUNICACAO - %s: %s",
                    indice, cst, combinacao.descricao(), erro.getClass().getSimpleName(), erro.getMessage());
        }
    }
}
