package com.fiscaladapter.documento.nfe.rvn;

import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;

import java.util.List;

public interface RegraNegocio {

    List<ViolacaoRegra> validar(NotaFiscalEletronica nfe);
}
