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
    void deveResolverTodosOsCincoServicosParaHomologacao() {
        for (TipoServicoSefaz servico : TipoServicoSefaz.values()) {
            assertThat(registry.obterUrl("RJ", TipoAmbiente.HOMOLOGACAO, servico)).startsWith("https://");
        }
    }

    @Test
    void deveFalharParaUfInexistente() {
        assertThatThrownBy(() -> registry.obterUrl("XX", TipoAmbiente.PRODUCAO, TipoServicoSefaz.STATUS_SERVICO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
