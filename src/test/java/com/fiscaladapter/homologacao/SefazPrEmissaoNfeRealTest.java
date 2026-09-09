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
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.nfe.EmissaoNfeOrquestrador;
import com.fiscaladapter.sefaz.nfe.NfeAutorizacaoClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
import com.fiscaladapter.sefaz.nfe.NfeEpecClient;
import com.fiscaladapter.sefaz.nfe.SefazEndpointRegistry;
import com.fiscaladapter.sefaz.nfe.ResultadoEmissaoNfe;
import com.fiscaladapter.observabilidade.NfeEmissaoMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste MANUAL de emissao de NFe DE VERDADE contra a SEFAZ-PR de homologacao - o pipeline
 * completo (montar o dominio, gerar XML, assinar com o certificado real, validar contra o XSD
 * oficial, transmitir via mTLS, interpretar a resposta), usando exatamente o mesmo
 * EmissaoNfeOrquestrador que a API usa em producao, sem nenhum mock.
 *
 * Efeito colateral real: consome um numero de serie/documento no ambiente de HOMOLOGACAO da
 * SEFAZ-PR (sem valor fiscal, mas o numero fica registrado la) - por isso o numero usado e
 * derivado do horario atual, para nao colidir entre execucoes.
 *
 * Aceita qualquer resposta estruturada da SEFAZ como sucesso do teste (mesmo uma rejeicao) - o
 * que importa aqui e provar que o pipeline completo funciona ponta a ponta contra a infraestrutura
 * real; cStat 100 (autorizada) e o resultado ideal mas nao e garantido (depende dos dados fiscais
 * usados aqui corresponderem ao cadastro real do CNPJ do certificado, o que este teste nao tem
 * como verificar de antemao).
 *
 * Excluido do `mvn verify` por padrao (@Tag("homologacao-real") + excludedGroups no pom.xml) -
 * mesmo mecanismo do SefazPrConectividadeRealTest (ver javadoc la para instrucoes de como rodar).
 */
@Tag("homologacao-real")
@EnabledIfEnvironmentVariable(named = "FISCALADAPTER_TESTE_CERT_CAMINHO", matches = ".+")
class SefazPrEmissaoNfeRealTest {

    private static final String UF = "PR";
    private static final String CODIGO_MUNICIPIO_CURITIBA = "4106902";

    @Test
    void deveEmitirNfeDeTesteEReceberUmaRespostaRealDaSefaz() throws Exception {
        CertificadoCarregado certificado = carregarCertificadoDoAmbiente();
        String cnpjEmitente = certificado.info().cnpj();
        assertThat(cnpjEmitente).as("o certificado precisa ter um CNPJ ICP-Brasil valido para emitir").isNotBlank();

        NotaFiscalEletronica nfe = montarNfeDeTeste(cnpjEmitente);

        EmissaoNfeOrquestrador orquestrador = montarOrquestradorReal();

        ResultadoEmissaoNfe resultado = orquestrador.emitir(nfe, certificado);

        System.out.println("SEFAZ-" + UF + " homologacao respondeu para a chave " + resultado.chaveAcesso()
                + ": cStat=" + resultado.autorizacao().codigoStatus()
                + " motivo=\"" + resultado.autorizacao().motivo() + "\""
                + " autorizada=" + resultado.autorizacao().autorizada()
                + " numeroProtocolo=" + resultado.autorizacao().numeroProtocolo()
                + " viaContingencia=" + resultado.viaContingencia()
                + " viaEpec=" + resultado.viaEpec());

        assertThat(resultado.chaveAcesso()).hasSize(44);
        assertThat(resultado.autorizacao().codigoStatus()).isNotBlank();
        assertThat(resultado.autorizacao().motivo()).isNotBlank();
    }

    private NotaFiscalEletronica montarNfeDeTeste(String cnpjEmitente) {
        Endereco enderecoEmitente = new Endereco("Rua de Teste", "100", "Centro",
                CODIGO_MUNICIPIO_CURITIBA, "Curitiba", UF, "80000000", "4130000000");
        // crt=3 (Regime Normal) + ICMS00: par mais comum, mas o regime tributario REAL do CNPJ do
        // certificado e desconhecido por este teste - se nao bater com o cadastro na SEFAZ, o
        // resultado mais provavel e uma rejeicao (nao uma falha de comunicacao), o que ainda prova
        // que o pipeline funciona ponta a ponta.
        Emitente emitente = new Emitente(cnpjEmitente, "EMPRESA DE TESTE - HOMOLOGACAO", "TESTE",
                "1234567890", "3", enderecoEmitente);

        Endereco enderecoDestinatario = new Endereco("Avenida de Teste", "200", "Centro",
                CODIGO_MUNICIPIO_CURITIBA, "Curitiba", UF, "80000000", null);
        // CPF de teste com digitos verificadores validos, amplamente usado so para testes (nao
        // pertence a uma pessoa real).
        Destinatario destinatario = new Destinatario("11144477735", "CLIENTE DE TESTE - HOMOLOGACAO",
                "9", null, null, enderecoDestinatario);

        ImpostoItem imposto = new ImpostoItem("ICMS00", "0", "00",
                BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(18.00).setScale(2), BigDecimal.valueOf(18.00).setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.valueOf(1.65).setScale(2), BigDecimal.valueOf(7.60).setScale(2));
        ItemNota item = new ItemNota(1, "TESTE001", "PRODUTO DE TESTE - HOMOLOGACAO", "61099010", "5102", "UN",
                BigDecimal.ONE.setScale(4), BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(100.00).setScale(2), imposto);

        // numero derivado do horario atual para nao colidir com uma chave ja usada numa execucao
        // anterior deste teste (SEFAZ rejeitaria com "204 - duplicidade" numa reexecucao com o
        // mesmo numero).
        long numero = System.currentTimeMillis() / 1000 % 100_000_000L;
        IdentificacaoNfe ide = new IdentificacaoNfe(UF, "VENDA DE MERCADORIA", 1, numero,
                LocalDate.now(ZoneId.of("America/Sao_Paulo")), TipoAmbiente.HOMOLOGACAO, 1, true,
                CODIGO_MUNICIPIO_CURITIBA, TipoDocumentoFiscal.NFE);

        DetalhePagamento pagamento = new DetalhePagamento("01", BigDecimal.valueOf(100.00).setScale(2));

        return new NotaFiscalEletronica(ide, emitente, destinatario, List.of(item), List.of(pagamento));
    }

    /** Monta o orquestrador de emissao com os componentes REAIS (nenhum mock) - o mesmo caminho que a API usa em producao. */
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
}
