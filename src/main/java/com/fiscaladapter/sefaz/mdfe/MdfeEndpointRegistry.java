package com.fiscaladapter.sefaz.mdfe;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Enderecos dos webservices do MDF-e 3.00 por UF/ambiente, carregados de
 * mdfe-webservices.properties (FIS-45) - 100% centralizado na SVRS (todas
 * as 27 UFs delegam para o mesmo endereco). Fonte: ACBrMDFeServicos.ini do
 * projeto ACBr, mesma familia de referencia usada para
 * nfe-webservices.properties/cte-webservices.properties.
 */
@Component
public class MdfeEndpointRegistry {

    private final Properties propriedades = new Properties();

    public MdfeEndpointRegistry() {
        try (InputStream in = new ClassPathResource("mdfe-webservices.properties").getInputStream()) {
            propriedades.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar mdfe-webservices.properties", e);
        }
    }

    public String obterUrl(String uf, TipoAmbiente ambiente, TipoServicoMdfe servico) {
        String chave = uf.toUpperCase() + "." + ambiente.name() + "." + servico.name();
        String url = propriedades.getProperty(chave);
        if (url == null) {
            throw new IllegalArgumentException("Endpoint de MDF-e nao cadastrado para " + chave);
        }
        return url;
    }
}
