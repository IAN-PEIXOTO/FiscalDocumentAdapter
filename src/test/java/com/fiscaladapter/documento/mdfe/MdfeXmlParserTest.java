package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Prova o caminho de reconstrucao do dominio a partir do XML arquivado, usado para reimprimir o DAMDFE apos o encerramento (FIS-49). */
class MdfeXmlParserTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final MdfeXmlGenerator xmlGenerator = new MdfeXmlGenerator(chaveAcessoService);

    @Test
    void deveReconstruirMdfeAPartirDoXmlGerado() {
        Mdfe original = MdfeTestFixture.mdfeDeExemplo();
        String chave = chaveAcessoService.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199", "58", 1, 42, 1, "10000001");
        String xml = xmlGenerator.gerar(original, chave);

        Mdfe reconstruido = MdfeXmlParser.paraDominio(xml);

        assertThat(reconstruido.identificacao().serie()).isEqualTo(original.identificacao().serie());
        assertThat(reconstruido.identificacao().numero()).isEqualTo(original.identificacao().numero());
        assertThat(reconstruido.identificacao().ufInicio()).isEqualTo(original.identificacao().ufInicio());
        assertThat(reconstruido.identificacao().ufFim()).isEqualTo(original.identificacao().ufFim());
        assertThat(reconstruido.identificacao().municipioCarregamento()).isEqualTo(original.identificacao().municipioCarregamento());
        assertThat(reconstruido.emitente().cnpjSemMascara()).isEqualTo(original.emitente().cnpjSemMascara());
        assertThat(reconstruido.emitente().razaoSocial()).isEqualTo(original.emitente().razaoSocial());
        assertThat(reconstruido.rntrc()).isEqualTo(original.rntrc());
        assertThat(reconstruido.veiculoTracao().placa()).isEqualTo(original.veiculoTracao().placa());
        assertThat(reconstruido.condutores()).hasSize(1);
        assertThat(reconstruido.condutores().get(0).nome()).isEqualTo(original.condutores().get(0).nome());
        assertThat(reconstruido.condutores().get(0).cpfSemMascara()).isEqualTo(original.condutores().get(0).cpfSemMascara());
        assertThat(reconstruido.municipioDescarga()).isEqualTo(original.municipioDescarga());
        assertThat(reconstruido.chavesCteTransportados()).isEqualTo(original.chavesCteTransportados());
        assertThat(reconstruido.chavesNfeTransportadas()).isEqualTo(original.chavesNfeTransportadas());
        assertThat(reconstruido.valorCarga()).isEqualByComparingTo(original.valorCarga());
        assertThat(reconstruido.pesoBrutoKg()).isEqualByComparingTo(original.pesoBrutoKg());
    }
}
