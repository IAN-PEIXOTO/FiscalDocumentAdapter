package com.fiscaladapter.documento.nfe;

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

/** Valida o XML da NFe contra o XSD oficial (layout 4.00, Pacote de Liberacao 9). */
@Component
public class NfeXsdValidator {

    private final Schema schema;

    public NfeXsdValidator() {
        try {
            ClassPathResource recurso = new ClassPathResource("xsd/nfe/nfe_v4.00.xsd");
            StreamSource fonte = new StreamSource(recurso.getInputStream(), recurso.getURL().toString());
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.schema = factory.newSchema(fonte);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Falha ao carregar o XSD oficial da NFe", e);
        }
    }

    public void validar(String xml) {
        List<String> erros = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            // FIS-108: mesmo endurecimento contra XXE do FIS-107 (AssinaturaXmlService) - este
            // validador roda ANTES da assinatura no pipeline de emissao, entao e o primeiro parser
            // a tocar o XML gerado.
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
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
