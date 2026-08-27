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

/**
 * Valida o XML do MDF-e contra o XSD oficial (layout 3.00, pacote PL_MDFe_300b,
 * obtido de nfephp-org/sped-mdfe em 2026-08-27). Assim como no CT-e, o grupo
 * infModal usa xs:any/processContents="skip" - o conteudo do modal (rodo) nao
 * e validado estruturalmente por este validador.
 */
@Component
public class MdfeXsdValidator {

    private final Schema schema;

    public MdfeXsdValidator() {
        try {
            ClassPathResource recurso = new ClassPathResource("xsd/mdfe/mdfe_v3.00.xsd");
            StreamSource fonte = new StreamSource(recurso.getInputStream(), recurso.getURL().toString());
            // o XSD oficial usa maxOccurs="20000" (infCTe/infNFe) e "1000" (infMunDescarga),
            // acima do limite padrao (5000) que o Xerces do JDK impoe via jdk.xml.maxOccurLimit.
            System.setProperty("jdk.xml.maxOccurLimit", "0");
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.schema = factory.newSchema(fonte);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Falha ao carregar o XSD oficial do MDF-e", e);
        }
    }

    public void validar(String xml) {
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
