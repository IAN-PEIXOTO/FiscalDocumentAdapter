package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FIS-115: indIntermed="1" (operacao via site/plataforma de terceiros - marketplace) exige o
 * grupo infIntermed preenchido (CNPJ + identificador cadastrado no intermediador); indIntermed="0"
 * (sem intermediador, o caso mais comum) nao deve trazer esse grupo. Descoberto que a SEFAZ exige
 * indIntermed de fato (cStat 434) testando emissao real contra a SEFAZ-PR de homologacao.
 */
@Component
public class RegraIntermediadorConsistente implements RegraNegocio {

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        boolean comIntermediador = "1".equals(nfe.identificacao().indicadorIntermediador());
        boolean intermediadorInformado = nfe.intermediador() != null;

        if (comIntermediador && !intermediadorInformado) {
            return List.of(new ViolacaoRegra("RVN-007",
                    "indIntermed=1 (operacao via intermediador/marketplace) exige o grupo intermediador (CNPJ + identificador de cadastro) preenchido"));
        }
        if (!comIntermediador && intermediadorInformado) {
            return List.of(new ViolacaoRegra("RVN-007",
                    "Grupo intermediador informado, mas indIntermed nao esta como \"1\""));
        }
        return List.of();
    }
}
