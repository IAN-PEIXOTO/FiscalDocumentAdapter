package com.fiscaladapter.sefaz.cte;

/**
 * CT-e ja incluido em um MDF-e autorizado nao pode ser cancelado diretamente
 * (FIS-53) - a carga ja foi manifestada para transporte; cancelar o CT-e
 * "por baixo" do MDF-e deixaria o manifesto referenciando um documento
 * inexistente perante o fisco. O procedimento correto e primeiro cancelar
 * ou encerrar o MDF-e vinculado.
 *
 * Limitacao conhecida: este adapter nao rastreia se o MDF-e vinculado foi
 * posteriormente cancelado (o arquivamento legal, RetencaoDocumentoFiscalService,
 * guarda so o XML autorizado, sem status de cancelamento) - o bloqueio vale
 * emquanto existir QUALQUER MDF-e autorizado que referencie este CT-e, mesmo
 * que esse MDF-e tenha sido cancelado depois. Corrigir isso exigiria
 * rastrear o status de cancelamento dos documentos arquivados, fora do
 * escopo deste card.
 */
public class CteJaManifestadoEmMdfeException extends RuntimeException {

    public CteJaManifestadoEmMdfeException(String chaveMdfe) {
        super("CT-e ja manifestado no MDF-e " + chaveMdfe + " - cancele ou encerre o MDF-e antes de cancelar este CT-e");
    }
}
