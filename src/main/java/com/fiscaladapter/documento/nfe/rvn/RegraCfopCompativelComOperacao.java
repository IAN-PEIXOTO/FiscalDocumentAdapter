package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.ItemNota;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * CFOP deve comecar com 5 quando emitente e destinatario estao na mesma UF
 * (operacao interna) ou com 6 quando estao em UFs diferentes (interestadual).
 * Essa e uma das rejeicoes mais comuns na pratica (CFOP incompativel com a
 * UF de destino).
 */
@Component
public class RegraCfopCompativelComOperacao implements RegraNegocio {

    @Override
    public List<ViolacaoRegra> validar(NotaFiscalEletronica nfe) {
        // sem destinatario (NFC-e para consumidor nao identificado): operacao sempre interna
        boolean operacaoInterna = nfe.destinatario() == null
                || nfe.emitente().endereco().uf().equals(nfe.destinatario().endereco().uf());
        char prefixoEsperado = operacaoInterna ? '5' : '6';

        List<ViolacaoRegra> violacoes = new ArrayList<>();
        for (ItemNota item : nfe.itens()) {
            if (item.cfop().isEmpty() || item.cfop().charAt(0) != prefixoEsperado) {
                violacoes.add(new ViolacaoRegra("RVN-003",
                        "Item " + item.numero() + ": CFOP " + item.cfop() + " incompativel com operacao "
                                + (operacaoInterna ? "interna (deveria comecar com 5)" : "interestadual (deveria comecar com 6)")));
            }
        }
        return violacoes;
    }
}
