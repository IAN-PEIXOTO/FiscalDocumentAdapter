package com.fiscaladapter.api.mdfe;

import com.fiscaladapter.sefaz.rejeicao.CategoriaErroSefaz;

import java.util.List;

/**
 * chavesCteTransportados/chavesNfeTransportadas (FIS-54, "documentos vinculados") vem do XML
 * arquivado por este adapter na emissao (RetencaoDocumentoFiscalService + MdfeXmlParser, FIS-49) -
 * a SEFAZ nao devolve essa lista na consulta de situacao. Ficam vazias se o MDF-e consultado nao
 * foi emitido por este adapter. encerrado (mesmo criterio de aceite) reflete se um encerramento
 * ja foi registrado (MdfeEncerramentoRegistroService).
 */
public record ConsultaMdfeResponse(
        String chaveAcesso,
        boolean autorizada,
        String codigoStatusSefaz,
        String motivoSefaz,
        String numeroProtocolo,
        boolean encerrado,
        List<String> chavesCteTransportados,
        List<String> chavesNfeTransportadas,
        String mensagemErro,
        CategoriaErroSefaz categoriaErro
) {
}
