package com.fiscaladapter.sefaz.nfe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova o mapeamento UF -> SVC de contingencia (FIS-75), revalidado contra fontes oficiais de
 * fazendas estaduais - ver Javadoc de MapeamentoContingenciaSvc para as fontes usadas.
 */
class MapeamentoContingenciaSvcTest {

    @Test
    void paEPiDevemUsarSvcRs() {
        // FIS-75: estavam mapeados incorretamente para SVC-AN antes desta correcao.
        assertThat(MapeamentoContingenciaSvc.svcPara("PA")).isEqualTo(ServicoContingenciaSvc.SVC_RS);
        assertThat(MapeamentoContingenciaSvc.svcPara("PI")).isEqualTo(ServicoContingenciaSvc.SVC_RS);
    }

    @Test
    void peDeveUsarSvcRs() {
        assertThat(MapeamentoContingenciaSvc.svcPara("PE")).isEqualTo(ServicoContingenciaSvc.SVC_RS);
    }

    @Test
    void spDeveUsarSvcAn() {
        assertThat(MapeamentoContingenciaSvc.svcPara("SP")).isEqualTo(ServicoContingenciaSvc.SVC_AN);
    }

    @Test
    void deveMapearTodasAs27Ufs() {
        String[] todasAsUfs = {"AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS",
                "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"};

        for (String uf : todasAsUfs) {
            assertThat(MapeamentoContingenciaSvc.svcPara(uf)).isNotNull();
        }
    }

    @Test
    void deveRejeitarUfDesconhecida() {
        assertThatThrownBy(() -> MapeamentoContingenciaSvc.svcPara("XX"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
