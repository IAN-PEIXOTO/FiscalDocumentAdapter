package com.fiscaladapter.documento.nfse;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Estrutura de mapeamento extensivel por municipio (FIS-20): resolve, a
 * partir do codigo IBGE do municipio de prestacao, qual {@link PadraoNfse} e
 * qual {@link NfseXmlGenerator} usar. Municipios sem entrada explicita em
 * {@code nfse-municipios.properties} caem no padrao ABRASF (o mais adotado).
 * Adicionar suporte a um novo padrao ou a um municipio com variacao propria:
 * implementar um novo NfseXmlGenerator, registra-lo como @Component (entra
 * automaticamente aqui) e mapear o(s) municipio(s) no properties.
 */
@Component
public class NfseXmlGeneratorRegistry {

    private static final PadraoNfse PADRAO_DEFAULT = PadraoNfse.ABRASF_V2_01;

    private final Map<PadraoNfse, NfseXmlGenerator> geradoresPorPadrao;
    private final Map<String, PadraoNfse> padraoPorMunicipio;

    public NfseXmlGeneratorRegistry(List<NfseXmlGenerator> geradores) {
        this.geradoresPorPadrao = new EnumMap<>(PadraoNfse.class);
        for (NfseXmlGenerator gerador : geradores) {
            geradoresPorPadrao.put(gerador.padraoSuportado(), gerador);
        }
        this.padraoPorMunicipio = carregarMapeamentoDeMunicipios();
    }

    public String gerar(String codigoMunicipioIbge, Nfse nfse) {
        PadraoNfse padrao = padraoPorMunicipio.getOrDefault(codigoMunicipioIbge, PADRAO_DEFAULT);
        NfseXmlGenerator gerador = geradoresPorPadrao.get(padrao);
        if (gerador == null) {
            throw new IllegalStateException("Nenhum NfseXmlGenerator registrado para o padrao " + padrao);
        }
        return gerador.gerar(nfse);
    }

    public PadraoNfse padraoPara(String codigoMunicipioIbge) {
        return padraoPorMunicipio.getOrDefault(codigoMunicipioIbge, PADRAO_DEFAULT);
    }

    private Map<String, PadraoNfse> carregarMapeamentoDeMunicipios() {
        Properties propriedades = new Properties();
        try (InputStream fluxo = new ClassPathResource("nfse-municipios.properties").getInputStream()) {
            propriedades.load(fluxo);
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao carregar nfse-municipios.properties", e);
        }
        Map<String, PadraoNfse> mapa = new java.util.HashMap<>();
        for (String codigoMunicipio : propriedades.stringPropertyNames()) {
            mapa.put(codigoMunicipio, PadraoNfse.valueOf(propriedades.getProperty(codigoMunicipio)));
        }
        return mapa;
    }
}
