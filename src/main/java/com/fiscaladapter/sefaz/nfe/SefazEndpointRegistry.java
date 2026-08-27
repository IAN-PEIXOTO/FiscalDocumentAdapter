package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Enderecos dos webservices da NFe 4.00 por UF/ambiente, carregados de
 * nfe-webservices.properties. Fonte dos dados: ACBrNFeServicos.ini do
 * projeto ACBr (https://github.com/frones/ACBr), a lista mais completa e
 * atualizada disponivel publicamente para esse fim. Ver cabecalho do
 * properties para detalhes de origem/data.
 */
@Component
public class SefazEndpointRegistry {

    private final Properties propriedades = new Properties();

    public SefazEndpointRegistry() {
        try (InputStream in = new ClassPathResource("nfe-webservices.properties").getInputStream()) {
            propriedades.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar nfe-webservices.properties", e);
        }
    }

    public String obterUrl(String uf, TipoAmbiente ambiente, TipoServicoSefaz servico) {
        String chave = uf.toUpperCase() + "." + ambiente.name() + "." + servico.name();
        String url = propriedades.getProperty(chave);
        if (url == null) {
            throw new IllegalArgumentException("Endpoint nao cadastrado para " + chave);
        }
        return url;
    }
}
