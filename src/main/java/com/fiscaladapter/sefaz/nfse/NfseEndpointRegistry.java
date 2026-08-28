package com.fiscaladapter.sefaz.nfse;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Enderecos dos webservices municipais de NFS-e, por municipio (codigo IBGE)
 * e ambiente, carregados de nfse-webservices.properties. Mesmo formato de
 * chave do SefazEndpointRegistry (NFe), trocando UF por codigo IBGE do
 * municipio - ver o cabecalho do properties para o porque de nao existir um
 * catalogo pronto (diferente da NFe, aqui cada municipio precisa ser
 * cadastrado manualmente durante o onboarding do cliente daquela prefeitura).
 */
@Component
public class NfseEndpointRegistry {

    private final Properties propriedades = new Properties();

    public NfseEndpointRegistry() {
        try (InputStream in = new ClassPathResource("nfse-webservices.properties").getInputStream()) {
            propriedades.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar nfse-webservices.properties", e);
        }
    }

    public String obterUrl(String codigoIbgeMunicipio, TipoAmbiente ambiente, TipoServicoAbrasfNfse servico) {
        String chave = codigoIbgeMunicipio + "." + ambiente.name() + "." + servico.name();
        String url = propriedades.getProperty(chave);
        if (url == null) {
            throw new IllegalArgumentException("Endpoint de NFS-e nao cadastrado para " + chave
                    + " - cadastre o endpoint da prefeitura em nfse-webservices.properties durante o onboarding do cliente");
        }
        return url;
    }
}
