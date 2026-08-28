package com.fiscaladapter.retencao;

import java.time.LocalDate;

public record DocumentoFiscalResponse(String chaveAcesso, String tipoDocumento, String numeroProtocolo,
                                       LocalDate dataEmissao, String xmlAssinado) {
}
