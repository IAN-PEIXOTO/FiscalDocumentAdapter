package com.fiscaladapter.api.cte;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

import java.util.List;

/**
 * notasFiscaisTransportadas (FIS-44, criterio de aceite 3) vem do XML arquivado por este adapter
 * na emissao (RetencaoDocumentoFiscalService) - a SEFAZ nao devolve essa lista na consulta de
 * situacao, so o cStat/protocolo. Fica vazia se o CT-e nao foi emitido por este adapter (nunca
 * arquivado aqui) ou se nao transportava nenhuma NF-e.
 */
public record ConsultaCteResponse(
        String chaveAcesso,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        List<String> notasFiscaisTransportadas,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
