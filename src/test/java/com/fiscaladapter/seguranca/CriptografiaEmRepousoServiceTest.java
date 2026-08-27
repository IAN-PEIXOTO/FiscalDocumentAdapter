package com.fiscaladapter.seguranca;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CriptografiaEmRepousoServiceTest {

    private static final String CHAVE_TESTE = Base64.getEncoder().encodeToString("chave-de-32-bytes-para-testes-11".getBytes());

    private final CriptografiaEmRepousoService servico = new CriptografiaEmRepousoService(CHAVE_TESTE);

    @Test
    void deveCriptografarEDescriptografarDeVolta() {
        String textoOriginal = "{\"chaveAcesso\":\"35260112345678000199550010000000011000000015\"}";

        String criptografado = servico.criptografar(textoOriginal);

        assertThat(criptografado).isNotEqualTo(textoOriginal);
        assertThat(servico.descriptografar(criptografado)).isEqualTo(textoOriginal);
    }

    @Test
    void cadaChamadaDeveGerarCiphertextDiferenteMesmoParaOMesmoTexto() {
        String textoOriginal = "dado sensivel repetido";

        String primeiro = servico.criptografar(textoOriginal);
        String segundo = servico.criptografar(textoOriginal);

        assertThat(primeiro).isNotEqualTo(segundo);
        assertThat(servico.descriptografar(primeiro)).isEqualTo(textoOriginal);
        assertThat(servico.descriptografar(segundo)).isEqualTo(textoOriginal);
    }

    @Test
    void deveFalharAoDescriptografarComChaveErrada() {
        String outraChave = Base64.getEncoder().encodeToString("outra-chave-de-32-bytes-teste-11".getBytes());
        CriptografiaEmRepousoService servicoComOutraChave = new CriptografiaEmRepousoService(outraChave);

        String criptografado = servico.criptografar("dado sensivel");

        assertThatThrownBy(() -> servicoComOutraChave.descriptografar(criptografado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveRejeitarChaveComTamanhoInvalido() {
        String chaveCurta = Base64.getEncoder().encodeToString("chave-curta".getBytes());

        assertThatThrownBy(() -> new CriptografiaEmRepousoService(chaveCurta))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
