package com.fiscaladapter.documento.nfse;

import com.fiscaladapter.documento.nfse.abrasf.AbrasfNfseXmlGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NfseXmlGeneratorRegistryTest {

    private final NfseXmlGeneratorRegistry registry = new NfseXmlGeneratorRegistry(List.of(new AbrasfNfseXmlGenerator()));

    @Test
    void deveUsarAbrasfComoPadraoParaMunicipioSemMapeamentoExplicito() {
        assertThat(registry.padraoPara("9999999")).isEqualTo(PadraoNfse.ABRASF_V2_01);
    }

    @Test
    void deveGerarOXmlUsandoOGeradorDoPadraoResolvidoParaOMunicipio() {
        Nfse nfse = NfseTestFixture.nfseDeExemplo();

        String xml = registry.gerar("3550308", nfse);

        assertThat(xml).contains("GerarNfseEnvio");
    }
}
