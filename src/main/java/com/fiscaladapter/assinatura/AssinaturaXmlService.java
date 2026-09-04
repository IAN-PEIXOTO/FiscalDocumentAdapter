package com.fiscaladapter.assinatura;

import com.fiscaladapter.certificado.CertificadoCarregado;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.keyinfo.X509Data;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

/**
 * Assina digitalmente o elemento infNFe conforme exigido pela SEFAZ:
 * assinatura envelopada (enveloped), Canonical XML 1.0 (nao exclusiva),
 * digest SHA-1 e algoritmo RSA-SHA1 - o padrao XML-DSig historico da NFe,
 * mantido por compatibilidade mesmo sendo criptograficamente datado.
 */
@Service
public class AssinaturaXmlService {

    public String assinar(String xmlSemAssinatura, String idElementoAssinado, CertificadoCarregado certificado) {
        try {
            Document documento = parse(xmlSemAssinatura);
            Element elementoAssinado = localizarPorId(documento, idElementoAssinado);
            elementoAssinado.setIdAttribute("Id", true);

            XMLSignatureFactory factory = XMLSignatureFactory.getInstance("DOM");

            Transform enveloped = factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null);
            Transform c14n = factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null);
            DigestMethod digestMethod = factory.newDigestMethod(DigestMethod.SHA1, null);

            Reference reference = factory.newReference(
                    "#" + idElementoAssinado,
                    digestMethod,
                    List.of(enveloped, c14n),
                    null,
                    null
            );

            SignedInfo signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (javax.xml.crypto.dsig.spec.C14NMethodParameterSpec) null),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(reference)
            );

            X509Certificate certificadoX509 = (X509Certificate) certificado.chaveEEntidade().getCertificate();
            KeyInfoFactory keyInfoFactory = factory.getKeyInfoFactory();
            X509Data x509Data = keyInfoFactory.newX509Data(Collections.singletonList(certificadoX509));
            KeyInfo keyInfo = keyInfoFactory.newKeyInfo(Collections.singletonList(x509Data));

            XMLSignature assinatura = factory.newXMLSignature(signedInfo, keyInfo);

            DOMSignContext contexto = new DOMSignContext(
                    certificado.chaveEEntidade().getPrivateKey(),
                    documento.getDocumentElement());

            assinatura.sign(contexto);

            return serializar(documento);
        } catch (Exception e) {
            throw new AssinaturaDigitalException("Falha ao assinar digitalmente o XML", e);
        }
    }

    private Element localizarPorId(Document documento, String id) {
        NodeList todos = documento.getElementsByTagName("*");
        for (int i = 0; i < todos.getLength(); i++) {
            Element candidato = (Element) todos.item(i);
            if (id.equals(candidato.getAttribute("Id"))) {
                return candidato;
            }
        }
        throw new IllegalArgumentException("Elemento com Id='" + id + "' nao encontrado no XML");
    }

    /**
     * FIS-107: XML fiscal (NFe/CTe/MDFe/evento) nunca tem DOCTYPE legitimamente - desabilitar por
     * completo fecha qualquer risco de XXE (entidade externa resolvida no servidor, causando
     * vazamento de arquivo local ou SSRF), independente de existir ou nao, hoje, algum ponto de
     * injecao de XML ainda nao descoberto num dos geradores (varios ja foram achados e corrigidos
     * ao longo das auditorias - FIS-57/58/67).
     */
    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String serializar(Document documento) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        StringWriter destino = new StringWriter();
        transformer.transform(new DOMSource(documento), new StreamResult(destino));
        return destino.toString();
    }
}
