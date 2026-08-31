package com.fiscaladapter.documento.nfe;

import com.fiscaladapter.documento.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChaveAcessoServiceTest {

    private final ChaveAcessoService service = new ChaveAcessoService();

    @Test
    void digitoVerificadorDeveSeguirAlgoritmoModulo11ComPesosCiclicosDaDireitaParaEsquerda() {
        // corpo de teste calculado manualmente: pesos 2,3,4,5,6,7,8,9 da direita para
        // a esquerda sobre "1234567890123" resulta em soma=286, 286 % 11 = 0 -> DV=0
        assertThat(service.calcularDigitoVerificador("1234567890123")).isZero();
    }

    @Test
    void chaveDeAcessoDeveTer44DigitosENaComposicaoCorreta() {
        String chave = service.gerar("SP", LocalDate.of(2026, 3, 15), "12.345.678/0001-99",
                service.modeloPara(TipoDocumentoFiscal.NFE), 1, 42, 1, "10000001");

        assertThat(chave).hasSize(44);
        assertThat(chave).startsWith("35"); // codigo UF de SP
        assertThat(chave.substring(2, 6)).isEqualTo("2603"); // AAMM
        assertThat(chave.substring(6, 20)).isEqualTo("12345678000199"); // CNPJ sem mascara
        assertThat(chave.substring(20, 22)).isEqualTo("55"); // modelo NFe
        assertThat(chave.substring(22, 25)).isEqualTo("001"); // serie
        assertThat(chave.substring(25, 34)).isEqualTo("000000042"); // numero do documento
    }

    @Test
    void deveExtrairModeloDocumentoDaChaveDeAcesso() {
        String chaveNfe = service.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199",
                service.modeloPara(TipoDocumentoFiscal.NFE), 1, 42, 1, "10000001");
        String chaveNfce = service.gerar("SP", LocalDate.of(2026, 3, 15), "12345678000199",
                service.modeloPara(TipoDocumentoFiscal.NFCE), 1, 42, 1, "10000001");

        assertThat(service.modeloDocumento(chaveNfe)).isEqualTo("55");
        assertThat(service.modeloDocumento(chaveNfce)).isEqualTo("65");
    }

    @Test
    void deveRejeitarCodigoNumericoComTamanhoInvalido() {
        assertThatThrownBy(() -> service.gerar("SP", LocalDate.now(), "12345678000199", "55", 1, 1, 1, "123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarCnpjInvalido() {
        assertThatThrownBy(() -> service.gerar("SP", LocalDate.now(), "123", "55", 1, 1, 1, "10000001"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
