package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Roda todas as Regras de Validacao de Negocio (RVN) cadastradas como beans
 * Spring. Executa antes da assinatura/envio, para nao gastar uma tentativa
 * de protocolo com a SEFAZ em algo que ja sabemos que sera rejeitado.
 */
@Service
public class RegraNegocioService {

    private final List<RegraNegocio> regras;

    public RegraNegocioService(List<RegraNegocio> regras) {
        this.regras = regras;
    }

    public void validar(NotaFiscalEletronica nfe) {
        List<ViolacaoRegra> violacoes = regras.stream()
                .flatMap(regra -> regra.validar(nfe).stream())
                .toList();

        if (!violacoes.isEmpty()) {
            throw new RegraNegocioVioladaException(violacoes);
        }
    }
}
