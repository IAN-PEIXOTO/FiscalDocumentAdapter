package com.fiscaladapter.sefaz.cte;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Enderecos dos webservices do CT-e 4.00 por UF/ambiente, carregados de
 * cte-webservices.properties (FIS-44) - infraestrutura totalmente separada
 * da NFe/NFC-e (dominios cte.*.gov.br, nao nfe.*.gov.br, exceto onde a
 * propria UF delega para o mesmo host da SEFAZ-SP). Fonte:
 * ACBrCTeServicos.ini do projeto ACBr, mesma familia de referencia usada
 * para nfe-webservices.properties.
 */
@Component
public class CteEndpointRegistry {

    private final Properties propriedades = new Properties();

    public CteEndpointRegistry() {
        try (InputStream in = new ClassPathResource("cte-webservices.properties").getInputStream()) {
            propriedades.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar cte-webservices.properties", e);
        }
    }

    public String obterUrl(String uf, TipoAmbiente ambiente, TipoServicoCte servico) {
        String chave = uf.toUpperCase() + "." + ambiente.name() + "." + servico.name();
        String url = propriedades.getProperty(chave);
        if (url == null) {
            throw new IllegalArgumentException("Endpoint de CT-e nao cadastrado para " + chave);
        }
        return url;
    }
}
