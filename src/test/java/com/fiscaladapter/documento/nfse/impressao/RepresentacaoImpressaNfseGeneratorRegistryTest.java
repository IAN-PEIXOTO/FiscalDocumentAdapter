package com.fiscaladapter.documento.nfse.impressao;

import com.fiscaladapter.documento.nfse.Nfse;
import com.fiscaladapter.documento.nfse.NfseTestFixture;
import com.fiscaladapter.sefaz.nfse.NfseResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Prova a extensibilidade por municipio (FIS-50, criterio de aceite 2): um layout customizado registrado tem prioridade sobre o generico. */
class RepresentacaoImpressaNfseGeneratorRegistryTest {

    private final RepresentacaoImpressaNfseGenericaGenerator generico = new RepresentacaoImpressaNfseGenericaGenerator();

    @Test
    void deveCairNoLayoutGenericoQuandoNaoHaCustomizadoParaOMunicipio() {
        RepresentacaoImpressaNfseGeneratorRegistry registry =
                new RepresentacaoImpressaNfseGeneratorRegistry(List.of(generico), generico);

        assertThat(registry.resolver("3550308")).isSameAs(generico);
    }

    @Test
    void deveUsarLayoutCustomizadoQuandoRegistradoParaOMunicipio() {
        RepresentacaoImpressaNfseGenerator layoutCustomizado = new RepresentacaoImpressaNfseGenerator() {
            @Override
            public byte[] gerar(Nfse nfse, NfseResponse resposta) {
                return "layout-customizado".getBytes();
            }

            @Override
            public boolean suporta(String codigoMunicipioIbge) {
                return "3550308".equals(codigoMunicipioIbge);
            }
        };
        RepresentacaoImpressaNfseGeneratorRegistry registry =
                new RepresentacaoImpressaNfseGeneratorRegistry(List.of(layoutCustomizado, generico), generico);

        assertThat(registry.resolver("3550308")).isSameAs(layoutCustomizado);
        assertThat(registry.resolver("3304557")).isSameAs(generico);

        byte[] pdf = registry.gerar("3550308", NfseTestFixture.nfseDeExemplo(), new NfseResponse("1", "ABC", null, null));
        assertThat(new String(pdf)).isEqualTo("layout-customizado");
    }
}
