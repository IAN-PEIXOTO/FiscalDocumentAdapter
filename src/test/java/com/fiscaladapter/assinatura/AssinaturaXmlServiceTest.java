package com.fiscaladapter.assinatura;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssinaturaXmlServiceTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final NfeXmlGenerator xmlGenerator = new NfeXmlGenerator(chaveAcessoService);
    private final NfeXsdValidator xsdValidator = new NfeXsdValidator();
    private final AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();
    private final CertificadoDigitalService certificadoDigitalService = new CertificadoDigitalService();

    @Test
    void xmlAssinadoDeveSerValidoContraXsdETerAssinaturaCriptograficamenteVerificavel() throws Exception {
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();
        String chave = chaveAcessoService.gerar(
                nfe.identificacao().uf(), nfe.identificacao().dataEmissao(), nfe.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFE), nfe.identificacao().serie(),
                nfe.identificacao().numero(), 1, "10000001");
        String idInfNfe = "NFe" + chave;

        String xmlSemAssinatura = xmlGenerator.gerar(nfe, chave);

        CertificadoCarregado certificado = carregarCertificadoDeTeste();

        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, idInfNfe, certificado);

        // 1. estruturalmente valido contra o XSD oficial, agora com a assinatura presente
        assertThatCode(() -> xsdValidator.validar(xmlAssinado)).doesNotThrowAnyException();

        // 2. a assinatura e criptograficamente verificavel com a chave publica do certificado
        assertThat(assinaturaEhValida(xmlAssinado, idInfNfe, certificado)).isTrue();
    }

    /**
     * FIS-107: XML fiscal nunca tem DOCTYPE legitimamente - o parser precisa rejeitar por completo
     * em vez de resolver uma entidade externa (o que causaria vazamento de arquivo local/SSRF se
     * algum ponto de injecao de XML ainda nao descoberto conseguisse fazer um DOCTYPE chegar aqui).
     */
    @Test
    void deveRejeitarXmlComDoctypeEEntidadeExternaEmVezDeResolverAEntidade() throws Exception {
        String xmlMalicioso = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE NFe [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<NFe xmlns=\"http://www.portalfiscal.inf.br/nfe\"><infNFe Id=\"NFe123\">&xxe;</infNFe></NFe>";
        CertificadoCarregado certificado = carregarCertificadoDeTeste();

        assertThatThrownBy(() -> assinaturaXmlService.assinar(xmlMalicioso, "NFe123", certificado))
                .isInstanceOf(AssinaturaDigitalException.class);
    }

    private CertificadoCarregado carregarCertificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12(
                "12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(365))));
        return certificadoDigitalService.carregar(TestCertificadoFactory.comoStream(p12), senha);
    }

    private boolean assinaturaEhValida(String xmlAssinado, String idInfNfe, CertificadoCarregado certificado) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document documento = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xmlAssinado.getBytes(StandardCharsets.UTF_8)));

        Element infNFe = localizarPorId(documento, idInfNfe);
        infNFe.setIdAttribute("Id", true);

        NodeList assinaturas = documento.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature");
        Element signatureNode = (Element) assinaturas.item(0);

        var publicKey = ((java.security.cert.X509Certificate) certificado.chaveEEntidade().getCertificate()).getPublicKey();

        DOMValidateContext contexto = new DOMValidateContext(publicKey, signatureNode);
        // RSA-SHA1 e o algoritmo historico exigido pela NFe; a validacao segura do JDK
        // bloqueia SHA1 por padrao, entao desativamos so para este teste de verificacao.
        contexto.setProperty("org.jcp.xml.dsig.secureValidation", Boolean.FALSE);
        return XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(contexto).validate(contexto);
    }

    private Element localizarPorId(Document documento, String id) {
        NodeList todos = documento.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Element candidato = (Element) todos.item(i);
            if (id.equals(candidato.getAttribute("Id"))) {
                return candidato;
            }
        }
        throw new IllegalArgumentException("Elemento nao encontrado: " + id);
    }
}
