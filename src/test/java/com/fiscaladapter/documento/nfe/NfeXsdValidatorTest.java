package com.fiscaladapter.documento.nfe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Valida o XML gerado contra o XSD oficial da NFe (nfe_v4.00.xsd, Pacote de
 * Liberacao 9, obtido do espelho nfephp-org/sped-nfe). A unica falha esperada
 * aqui e a ausencia do elemento Signature: o XML gerado antes da assinatura
 * digital (FIS-4) e estruturalmente valido em tudo alem disso.
 */
class NfeXsdValidatorTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final NfeXmlGenerator generator = new NfeXmlGenerator(chaveAcessoService);
    private final NfeXsdValidator validator = new NfeXsdValidator();

    @Test
    void xmlGeradoDeveSerEstruturalmenteValidoExcetoPelaAssinaturaAindaAusente() {
        String xml = generator.gerar(NotaFiscalEletronicaTestFixture.notaDeExemplo());

        assertThatThrownBy(() -> validator.validar(xml))
                .isInstanceOf(XmlInvalidoException.class)
                .satisfies(e -> {
                    XmlInvalidoException invalido = (XmlInvalidoException) e;
                    assertThat(invalido.getErros()).hasSize(1);
                    assertThat(invalido.getErros().get(0)).contains("Signature");
                });
    }
}
