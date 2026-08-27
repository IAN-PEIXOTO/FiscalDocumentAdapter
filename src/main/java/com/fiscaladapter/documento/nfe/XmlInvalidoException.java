package com.fiscaladapter.documento.nfe;

import java.util.List;

/** XML nao esta conforme o XSD oficial. Cada item de {@link #getErros()} identifica linha e campo. */
public class XmlInvalidoException extends RuntimeException {

    private final List<String> erros;

    public XmlInvalidoException(List<String> erros) {
        super("XML invalido conforme o schema oficial: " + String.join(" | ", erros));
        this.erros = erros;
    }

    public List<String> getErros() {
        return erros;
    }
}
