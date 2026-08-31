package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SefazEndpointRegistryTest {

    private final SefazEndpointRegistry registry = new SefazEndpointRegistry();

    @Test
    void deveResolverEndpointDeSpProducao() {
        String url = registry.obterUrl("SP", TipoAmbiente.PRODUCAO, TipoServicoSefaz.STATUS_SERVICO);
        assertThat(url).isEqualTo("https://nfe.fazenda.sp.gov.br/ws/nfestatusservico4.asmx");
    }

    @Test
    void deveResolverEndpointDeUfQueUsaSvrs() {
        String url = registry.obterUrl("AC", TipoAmbiente.PRODUCAO, TipoServicoSefaz.AUTORIZACAO);
        assertThat(url).contains("svrs.rs.gov.br");
    }

    @Test
    void deveResolverTodosOsServicosPorUfParaHomologacao() {
        for (TipoServicoSefaz servico : TipoServicoSefaz.values()) {
            if (servico == TipoServicoSefaz.DISTRIBUICAO_DFE) {
                // DISTRIBUICAO_DFE (FIS-40) e nacional, nunca por UF - ver
                // deveResolverDistribuicaoDfeDoAmbienteNacional abaixo.
                continue;
            }
            assertThat(registry.obterUrl("RJ", TipoAmbiente.HOMOLOGACAO, servico)).startsWith("https://");
        }
    }

    @Test
    void deveResolverInutilizacaoParaTodasAs27UfsNosDoisAmbientes() {
        String[] ufs = {"AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MG", "MS", "MT", "PA", "PB",
                "PE", "PI", "PR", "RJ", "RN", "RO", "RR", "RS", "SC", "SE", "SP", "TO"};
        for (String uf : ufs) {
            assertThat(registry.obterUrl(uf, TipoAmbiente.PRODUCAO, TipoServicoSefaz.INUTILIZACAO)).startsWith("https://");
            assertThat(registry.obterUrl(uf, TipoAmbiente.HOMOLOGACAO, TipoServicoSefaz.INUTILIZACAO)).startsWith("https://");
        }
    }

    @Test
    void deveResolverRecepcaoEventoDoAmbienteNacionalParaEpecEManifestacao() {
        assertThat(registry.obterUrl("AN", TipoAmbiente.PRODUCAO, TipoServicoSefaz.RECEPCAO_EVENTO))
                .startsWith("https://");
        assertThat(registry.obterUrl("AN", TipoAmbiente.HOMOLOGACAO, TipoServicoSefaz.RECEPCAO_EVENTO))
                .startsWith("https://");
    }

    @Test
    void deveResolverDistribuicaoDfeDoAmbienteNacional() {
        assertThat(registry.obterUrl("AN", TipoAmbiente.PRODUCAO, TipoServicoSefaz.DISTRIBUICAO_DFE))
                .startsWith("https://");
        assertThat(registry.obterUrl("AN", TipoAmbiente.HOMOLOGACAO, TipoServicoSefaz.DISTRIBUICAO_DFE))
                .startsWith("https://");
    }

    @Test
    void deveFalharParaUfInexistente() {
        assertThatThrownBy(() -> registry.obterUrl("XX", TipoAmbiente.PRODUCAO, TipoServicoSefaz.STATUS_SERVICO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
