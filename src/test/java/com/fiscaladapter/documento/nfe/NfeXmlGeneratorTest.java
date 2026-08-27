package com.fiscaladapter.documento.nfe;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NfeXmlGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final NfeXmlGenerator generator = new NfeXmlGenerator(chaveAcessoService);

    @Test
    void deveGerarXmlValidoComEstruturaEsperada() throws Exception {
        NotaFiscalEletronica nfe = notaDeExemplo();

        String xml = generator.gerar(nfe);

        Document documento = parse(xml);

        Element infNFe = (Element) documento.getElementsByTagName("infNFe").item(0);
        assertThat(infNFe.getAttribute("Id")).startsWith("NFe35");
        assertThat(infNFe.getAttribute("versao")).isEqualTo("4.00");

        assertThat(textoDe(documento, "natOp")).isEqualTo("VENDA DE MERCADORIA");
        assertThat(textoDe(documento, "cUF")).isEqualTo("35");
        assertThat(textoDe(documento, "CNPJ")).isEqualTo("12345678000199");
        assertThat(textoDe(documento, "xProd")).isEqualTo("PRODUTO TESTE");
        assertThat(textoDe(documento, "vProd")).isEqualTo("100.00");
        assertThat(textoDe(documento, "vNF")).isEqualTo("100.00");

        NodeList itens = documento.getElementsByTagName("det");
        assertThat(itens.getLength()).isEqualTo(1);
        assertThat(((Element) itens.item(0)).getAttribute("nItem")).isEqualTo("1");
    }

    private String textoDe(Document documento, String tag) {
        return documento.getElementsByTagName(tag).item(0).getTextContent();
    }

    private Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private NotaFiscalEletronica notaDeExemplo() {
        Endereco enderecoEmitente = new Endereco("Rua Teste", "100", "Centro", "3550308", "Sao Paulo", "SP", "01000000", "1130000000");
        Emitente emitente = new Emitente("12.345.678/0001-99", "EMPRESA TESTE LTDA", "TESTE", "111222333", "1", enderecoEmitente);

        Endereco enderecoDestinatario = new Endereco("Av. Cliente", "200", "Jardins", "3550308", "Sao Paulo", "SP", "02000000", null);
        Destinatario destinatario = new Destinatario("987.654.321-00", "CLIENTE TESTE", "9", null, "cliente@teste.com", enderecoDestinatario);

        ImpostoItem imposto = new ImpostoItem("0", "00",
                BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(18.00).setScale(2), BigDecimal.valueOf(18.00).setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.valueOf(1.65).setScale(2), BigDecimal.valueOf(7.60).setScale(2));

        ItemNota item = new ItemNota(1, "PROD001", "PRODUTO TESTE", "61099010", "5102", "UN",
                BigDecimal.ONE.setScale(4), BigDecimal.valueOf(100.00).setScale(2), BigDecimal.valueOf(100.00).setScale(2), imposto);

        IdentificacaoNfe ide = new IdentificacaoNfe("SP", "VENDA DE MERCADORIA", 1, 42,
                LocalDate.of(2026, 3, 15), TipoAmbiente.HOMOLOGACAO, 1, true, "3550308");

        DetalhePagamento pagamento = new DetalhePagamento("01", BigDecimal.valueOf(100.00).setScale(2));

        return new NotaFiscalEletronica(ide, emitente, destinatario, List.of(item), List.of(pagamento));
    }
}
