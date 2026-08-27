package com.fiscaladapter.documento.mdfe;

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

class MdfeXmlGeneratorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final MdfeXmlGenerator generator = new MdfeXmlGenerator(chaveAcessoService);

    @Test
    void deveGerarXmlValidoComEstruturaEsperada() throws Exception {
        Mdfe mdfe = MdfeTestFixture.mdfeDeExemplo();

        String xml = generator.gerar(mdfe);
        Document documento = parse(xml);

        Element infMDFe = (Element) documento.getElementsByTagName("infMDFe").item(0);
        assertThat(infMDFe.getAttribute("Id")).startsWith("MDFe35");
        assertThat(infMDFe.getAttribute("versao")).isEqualTo("3.00");

        assertThat(textoDe(documento, "mod")).isEqualTo("58");
        assertThat(textoDe(documento, "modal")).isEqualTo("1");
        assertThat(textoDe(documento, "UFIni")).isEqualTo("SP");
        assertThat(textoDe(documento, "UFFim")).isEqualTo("RJ");
        assertThat(textoDe(documento, "placa")).isEqualTo("ABC1D23");
        assertThat(textoDe(documento, "tara")).isEqualTo("8000");
        assertThat(textoDe(documento, "tpRod")).isEqualTo("03");
        assertThat(textoDe(documento, "tpCar")).isEqualTo("02");
        assertThat(textoDe(documento, "RNTRC")).isEqualTo("12345678");
        assertThat(textoDe(documento, "qCTe")).isEqualTo("1");
        assertThat(textoDe(documento, "qNFe")).isEqualTo("1");
        assertThat(textoDe(documento, "vCarga")).isEqualTo("5000.00");

        NodeList condutores = documento.getElementsByTagName("condutor");
        assertThat(condutores.getLength()).isEqualTo(1);
        assertThat(((Element) condutores.item(0)).getElementsByTagName("xNome").item(0).getTextContent()).isEqualTo("JOAO DA SILVA");

        assertThat(documento.getElementsByTagName("infCTe").getLength()).isEqualTo(1);
        assertThat(documento.getElementsByTagName("infNFe").getLength()).isEqualTo(1);

        String chaveAcesso = infMDFe.getAttribute("Id").substring(4); // remove o prefixo "MDFe"
        assertThat(chaveAcesso).hasSize(44);
        assertThat(chaveAcesso.substring(20, 22)).isEqualTo("58"); // mod do MDF-e
    }

    @Test
    void deveValidarContraOXsdOficialSemErrosAlemDaAssinaturaAusente() throws Exception {
        Mdfe mdfe = MdfeTestFixture.mdfeDeExemplo();
        String xml = generator.gerar(mdfe);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new MdfeXsdValidator().validar(xml))
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
