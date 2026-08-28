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

    @Test
    void deveGerarGrupoIcms40ParaItemIsentoEValidoContraXsd() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaComImposto(
                NotaFiscalEletronicaTestFixture.impostoIcms40Isenta());

        String xml = generator.gerar(nfe);
        Document documento = parse(xml);

        Element grupoIcms40 = (Element) documento.getElementsByTagName("ICMS40").item(0);
        assertThat(grupoIcms40).isNotNull();
        assertThat(grupoIcms40.getElementsByTagName("CST").item(0).getTextContent()).isEqualTo("40");
        assertThat(grupoIcms40.getElementsByTagName("vBC").getLength()).isEqualTo(0);
        assertApenasAssinaturaAusente(xml);
    }

    @Test
    void deveGerarGrupoIcmsSn102ParaEmitenteDoSimplesNacionalEValidoContraXsd() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaComImposto(
                NotaFiscalEletronicaTestFixture.impostoIcmsSN102(), "1");

        String xml = generator.gerar(nfe);
        Document documento = parse(xml);

        assertThat(documento.getElementsByTagName("ICMSSN102").getLength()).isEqualTo(1);
        assertThat(textoDe(documento, "CSOSN")).isEqualTo("102");
        assertApenasAssinaturaAusente(xml);
    }

    @Test
    void deveGerarNfceSemDestinatarioComModeloETpPresCorretosEValidaContraXsd() throws Exception {
        NotaFiscalEletronica nfce = NotaFiscalEletronicaTestFixture.notaNfceSemDestinatario();

        String xml = generator.gerar(nfce);
        Document documento = parse(xml);

        assertThat(textoDe(documento, "mod")).isEqualTo("65");
        assertThat(textoDe(documento, "tpImp")).isEqualTo("4");
        assertThat(textoDe(documento, "indPres")).isEqualTo("1");
        assertThat(textoDe(documento, "idDest")).isEqualTo("1"); // sem dest -> assume operacao interna
        assertThat(documento.getElementsByTagName("dest").getLength()).isEqualTo(0);

        String chaveAcesso = ((Element) documento.getElementsByTagName("infNFe").item(0))
                .getAttribute("Id").substring(3); // remove o prefixo "NFe"
        assertThat(chaveAcesso.substring(20, 22)).isEqualTo("65"); // mod, layout cUF(2)+AAMM(4)+CNPJ(14)+mod(2)+...
        assertApenasAssinaturaAusente(xml);
    }

    /** XML sem assinatura (FIS-4 acontece depois) so pode falhar a validacao XSD por causa do Signature ausente. */
    private void assertApenasAssinaturaAusente(String xml) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new NfeXsdValidator().validar(xml))
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
