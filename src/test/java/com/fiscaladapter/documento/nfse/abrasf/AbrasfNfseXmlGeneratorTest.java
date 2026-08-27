package com.fiscaladapter.documento.nfse.abrasf;

import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.documento.nfse.NfseTestFixture;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AbrasfNfseXmlGeneratorTest {

    private final AbrasfNfseXmlGenerator generator = new AbrasfNfseXmlGenerator();
    private final AbrasfXsdValidator xsdValidator = new AbrasfXsdValidator();

    @Test
    void deveGerarXmlValidoComEstruturaEsperada() throws Exception {
        Nfse nfse = NfseTestFixture.nfseDeExemplo();

        String xml = generator.gerar(nfse);
        Document documento = parse(xml);

        assertThat(documento.getElementsByTagName("GerarNfseEnvio").getLength()).isEqualTo(1);
        assertThat(textoDe(documento, "Numero")).isEqualTo("42");
        assertThat(textoDe(documento, "Serie")).isEqualTo("1");
        assertThat(textoDe(documento, "Tipo")).isEqualTo("1");
        assertThat(textoDe(documento, "DataEmissao")).isEqualTo("2026-03-15");
        assertThat(textoDe(documento, "Competencia")).isEqualTo("2026-03-01");
        assertThat(textoDe(documento, "ValorServicos")).isEqualTo("1000.00");
        assertThat(textoDe(documento, "ItemListaServico")).isEqualTo("0107");
        assertThat(textoDe(documento, "IssRetido")).isEqualTo("2"); // false -> 2 = Nao
        assertThat(textoDe(documento, "Cnpj")).isEqualTo("12345678000199");
        assertThat(textoDe(documento, "Cpf")).isEqualTo("98765432100");
        assertThat(textoDe(documento, "RazaoSocial")).isEqualTo("CLIENTE TESTE");
        assertThat(textoDe(documento, "OptanteSimplesNacional")).isEqualTo("2");
    }

    @Test
    void deveValidarContraOXsdOficialSemNenhumErro() {
        Nfse nfse = NfseTestFixture.nfseDeExemplo();
        String xml = generator.gerar(nfse);

        assertThatCode(() -> xsdValidator.validar(xml)).doesNotThrowAnyException();
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
