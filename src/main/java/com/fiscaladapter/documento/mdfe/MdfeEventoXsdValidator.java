package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.XmlInvalidoException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida o XML do evento de MDF-e (envelope eventoMDFe_v3.00.xsd) e, em
 * separado, o conteudo especifico do evento de Encerramento
 * (evEncMDFe_v3.00.xsd) - o envelope usa xs:any/processContents="skip" em
 * detEvento, entao o conteudo interno precisa ser validado a parte contra o
 * XSD proprio do evento para provar corretude estrutural de fato.
 */
@Component
public class MdfeEventoXsdValidator {

    private static final Pattern TRECHO_EV_ENC_MDFE = Pattern.compile("<evEncMDFe>.*?</evEncMDFe>", Pattern.DOTALL);

    private final Schema schemaEnvelope;
    private final Schema schemaEncerramento;

    public MdfeEventoXsdValidator() {
        this.schemaEnvelope = carregar("xsd/mdfe/evento/eventoMDFe_v3.00.xsd");
        this.schemaEncerramento = carregar("xsd/mdfe/evento/evEncMDFe_v3.00.xsd");
    }

    private Schema carregar(String caminho) {
        try {
            ClassPathResource recurso = new ClassPathResource(caminho);
            StreamSource fonte = new StreamSource(recurso.getInputStream(), recurso.getURL().toString());
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(fonte);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Falha ao carregar XSD oficial de evento do MDF-e: " + caminho, e);
        }
    }

    public void validarEnvelope(String xml) {
        validar(schemaEnvelope, xml);
    }

    /** Extrai o trecho &lt;evEncMDFe&gt; do XML do evento e valida contra o XSD proprio do evEncMDFe. */
    public void validarEncerramento(String xmlDoEvento) {
        Matcher matcher = TRECHO_EV_ENC_MDFE.matcher(xmlDoEvento);
        if (!matcher.find()) {
            throw new IllegalArgumentException("XML nao contem o elemento <evEncMDFe>");
        }
        String fragmento = "<evEncMDFe xmlns=\"http://www.portalfiscal.inf.br/mdfe\">"
                + matcher.group().substring("<evEncMDFe>".length(), matcher.group().length() - "</evEncMDFe>".length())
                + "</evEncMDFe>";
        validar(schemaEncerramento, fragmento);
    }

    private void validar(Schema schema, String xml) {
        List<String> erros = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) {
                    // avisos nao bloqueiam o envio
                }

                @Override
                public void error(SAXParseException exception) {
                    erros.add(mensagem(exception));
                }

                @Override
                public void fatalError(SAXParseException exception) {
                    erros.add(mensagem(exception));
                }

                private String mensagem(SAXParseException e) {
                    return "Linha " + e.getLineNumber() + ": " + e.getMessage();
                }
            });
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException | IOException e) {
            erros.add(e.getMessage());
        }

        if (!erros.isEmpty()) {
            throw new XmlInvalidoException(erros);
        }
    }
}
