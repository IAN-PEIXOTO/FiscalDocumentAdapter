package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

/**
 * Encerramento = fim de percurso do MDF-e (FIS-45, criterio de aceite 2).
 * damdfePdfBase64 (FIS-49) - o DAMDFE reimpresso com a indicacao de
 * "encerrado" (AC3), vem nulo quando o encerramento nao foi aceito pela SEFAZ.
 */
public record EncerramentoMdfeResponse(
        String chaveAcesso,
        boolean encerrado,
        String codigoStatusSefaz,
        String motivoSefaz,
        String damdfePdfBase64,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
