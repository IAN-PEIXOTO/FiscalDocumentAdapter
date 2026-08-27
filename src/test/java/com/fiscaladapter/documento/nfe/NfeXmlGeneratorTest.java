package com.fiscaladapter.documento.nfe;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NfeXmlGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final NfeXmlGenerator generator = new NfeXmlGenerator(chaveAcessoService);

    @Test
    void deveGerarXmlValidoComEstruturaEsperada() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();

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
}
