package com.fiscaladapter.api;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.retencao.RetencaoDocumentoFiscalService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Prova a validacao de ambiente pos-emissao (FIS-64) contra o tpAmb gravado no XML arquivado na emissao. */
class AmbienteEmissaoValidatorTest {

    private static final String CHAVE_ACESSO = "35260112345678000199550010000000421000000019";

    private final RetencaoDocumentoFiscalService retencaoDocumentoFiscalService =
            Mockito.mock(RetencaoDocumentoFiscalService.class);

    private final AmbienteEmissaoValidator validator = new AmbienteEmissaoValidator(retencaoDocumentoFiscalService);

    @Test
    void devePermitirQuandoAmbienteInformadoBateComOOriginal() {
        String xmlComTpAmbHomologacao = "<NFe><infNFe><ide><tpAmb>2</tpAmb></ide></infNFe></NFe>";
        when(retencaoDocumentoFiscalService.recuperar(CHAVE_ACESSO))
                .thenReturn(Optional.of(documentoArquivado(xmlComTpAmbHomologacao)));

        assertThatCode(() -> validator.validar(CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO)).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarQuandoAmbienteInformadoDivergeDoOriginal() {
        String xmlComTpAmbProducao = "<NFe><infNFe><ide><tpAmb>1</tpAmb></ide></infNFe></NFe>";
        when(retencaoDocumentoFiscalService.recuperar(CHAVE_ACESSO))
                .thenReturn(Optional.of(documentoArquivado(xmlComTpAmbProducao)));

        assertThatThrownBy(() -> validator.validar(CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(CHAVE_ACESSO);
    }

    @Test
    void devePularAValidacaoQuandoDocumentoNaoFoiEmitidoPorEsteAdapter() {
        when(retencaoDocumentoFiscalService.recuperar(CHAVE_ACESSO)).thenReturn(Optional.empty());

        assertThatCode(() -> validator.validar(CHAVE_ACESSO, TipoAmbiente.PRODUCAO)).doesNotThrowAnyException();
    }

    private RetencaoDocumentoFiscalService.DocumentoRecuperado documentoArquivado(String xmlAssinado) {
        return new RetencaoDocumentoFiscalService.DocumentoRecuperado(
                CHAVE_ACESSO, "12345678000199", TipoDocumentoFiscal.NFE, "135260000000001",
                xmlAssinado, LocalDate.of(2026, 3, 15));
    }
}
