package com.fiscaladapter.sefaz.nfse;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NfseEndpointRegistryTest {

    @Test
    void deveFalharComMensagemClaraParaMunicipioNaoCadastrado() {
        NfseEndpointRegistry registry = new NfseEndpointRegistry();

        assertThatThrownBy(() -> registry.obterUrl("9999999", TipoAmbiente.HOMOLOGACAO, TipoServicoAbrasfNfse.GERAR_NFSE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9999999.HOMOLOGACAO.GERAR_NFSE");
    }
}
