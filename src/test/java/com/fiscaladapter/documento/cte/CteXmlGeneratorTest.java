package com.fiscaladapter.documento.cte;

import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.XmlInvalidoException;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CteXmlGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final CteXmlGenerator generator = new CteXmlGenerator(chaveAcessoService);

    @Test
    void deveGerarXmlValidoComEstruturaEsperada() throws Exception {
        Cte cte = CteTestFixture.cteDeExemplo();

        String xml = generator.gerar(cte);
        Document documento = parse(xml);

        Element infCte = (Element) documento.getElementsByTagName("infCte").item(0);
        assertThat(infCte.getAttribute("Id")).startsWith("CTe35");
        assertThat(infCte.getAttribute("versao")).isEqualTo("4.00");

        assertThat(textoDe(documento, "mod")).isEqualTo("57");
        assertThat(textoDe(documento, "modal")).isEqualTo("01");
        assertThat(textoDe(documento, "CFOP")).isEqualTo("6353");
        assertThat(textoDe(documento, "natOp")).isEqualTo("PRESTACAO DE SERVICO DE TRANSPORTE");
        assertThat(textoDe(documento, "vTPrest")).isEqualTo("1000.00");
        assertThat(textoDe(documento, "vRec")).isEqualTo("1000.00");
        assertThat(textoDe(documento, "RNTRC")).isEqualTo("12345678");

        NodeList infNFe = documento.getElementsByTagName("infNFe");
        assertThat(infNFe.getLength()).isEqualTo(1);
        assertThat(infNFe.item(0).getFirstChild().getTextContent()).isEqualTo("35260112345678000199550010000000421000000019");

        String chaveAcesso = infCte.getAttribute("Id").substring(3); // remove o prefixo "CTe"
        assertThat(chaveAcesso).hasSize(44);
        assertThat(chaveAcesso.substring(20, 22)).isEqualTo("57"); // mod do CT-e
    }

    @Test
    void deveValidarContraOXsdOficialSemErrosAlemDaAssinaturaAusente() throws Exception {
        Cte cte = CteTestFixture.cteDeExemplo();
        String xml = generator.gerar(cte);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CteXsdValidator().validar(xml))
                .isInstanceOf(XmlInvalidoException.class)
                .satisfies(e -> {
                    XmlInvalidoException invalido = (XmlInvalidoException) e;
                    assertThat(invalido.getErros()).hasSize(1);
                    assertThat(invalido.getErros().get(0)).contains("Signature");
                });
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
