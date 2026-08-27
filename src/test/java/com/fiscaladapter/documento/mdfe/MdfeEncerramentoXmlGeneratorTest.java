package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.documento.nfe.XmlInvalidoException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MdfeEncerramentoXmlGeneratorTest {

    private final MdfeEncerramentoXmlGenerator generator = new MdfeEncerramentoXmlGenerator();
    private final MdfeEventoXsdValidator xsdValidator = new MdfeEventoXsdValidator();

    @Test
    void deveGerarEventoDeEncerramentoComTpEventoECamposCorretos() {
        String chaveMdfe = "35260112345678000199580010000000421000000014";

        String xml = generator.gerar(chaveMdfe, "12345678000199", "35", "3550308",
                LocalDate.of(2026, 3, 20), "935260000000001", TipoAmbiente.HOMOLOGACAO);

        assertThat(xml).contains("<tpEvento>110112</tpEvento>")
                .contains("<descEvento>Encerramento</descEvento>")
                .contains("<chMDFe>" + chaveMdfe + "</chMDFe>")
                .contains("Id=\"ID110112" + chaveMdfe + "01\"");
    }

    @Test
    void deveValidarOConteudoDoEncerramentoContraOXsdOficialSemNenhumErro() {
        String chaveMdfe = "35260112345678000199580010000000421000000014";
        String xml = generator.gerar(chaveMdfe, "12345678000199", "35", "3550308",
                LocalDate.of(2026, 3, 20), "935260000000001", TipoAmbiente.HOMOLOGACAO);

        assertThatCode(() -> xsdValidator.validarEncerramento(xml)).doesNotThrowAnyException();
    }

    @Test
    void deveValidarOEnvelopeContraOXsdOficialTolerandoApenasAAssinaturaAusente() {
        String chaveMdfe = "35260112345678000199580010000000421000000014";
        String xml = generator.gerar(chaveMdfe, "12345678000199", "35", "3550308",
                LocalDate.of(2026, 3, 20), "935260000000001", TipoAmbiente.HOMOLOGACAO);

        assertThatThrownBy(() -> xsdValidator.validarEnvelope(xml))
                .isInstanceOf(XmlInvalidoException.class)
                .satisfies(e -> {
                    XmlInvalidoException invalido = (XmlInvalidoException) e;
                    assertThat(invalido.getErros()).hasSize(1);
                    assertThat(invalido.getErros().get(0)).contains("Signature");
                });
    }
}
