package com.fiscaladapter.sefaz.nfe;

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
 * Valida o XML do evento EPEC contra o XSD oficial (eventoEPEC_v1.00, obtido de
 * nfephp-org/sped-nfe em 2026-09-09) - o unico validador de evento que faltava neste projeto
 * (CTe/MDFe ja tem o seu, ver MdfeEventoXsdValidator), o que deixou passar despercebido, por
 * varias rodadas de auditoria por leitura de codigo, um campo <vST> extra em NfeEpecClient que
 * nao existe no schema oficial - so descoberto ao testar EPEC contra a SEFAZ de homologacao de
 * verdade (a SEFAZ respondeu cStat 493 "Rejeicao: Evento nao atende o Schema XML especifico").
 */
@Component
public class NfeEpecXsdValidator {

    private final Schema schema;

    public NfeEpecXsdValidator() {
        try {
            ClassPathResource recurso = new ClassPathResource("xsd/nfe/epec/eventoEPEC_v1.00.xsd");
            StreamSource fonte = new StreamSource(recurso.getInputStream(), recurso.getURL().toString());
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            this.schema = factory.newSchema(fonte);
        } catch (SAXException | IOException e) {
            throw new IllegalStateException("Falha ao carregar o XSD oficial do evento EPEC", e);
        }
    }

    public void validar(String xml) {
        List<String> erros = new ArrayList<>();
        try {
            Validator validator = schema.newValidator();
            // FIS-111: mesmo endurecimento contra XXE do FIS-107/108.
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
