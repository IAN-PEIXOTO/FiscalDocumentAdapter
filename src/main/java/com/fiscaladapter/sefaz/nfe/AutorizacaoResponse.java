package com.fiscaladapter.sefaz.nfe;

import java.util.Set;

public record AutorizacaoResponse(String codigoStatus, String motivo, String numeroProtocolo, String dhRecbto, boolean autorizada) {

    /**
     * cStat 100 = "Autorizado o uso da NF-e" (no lote sincrono, esse cStat vem no protNFe, nao no
     * retorno do lote). cStat 150 = "Autorizado o uso da NF-e, autorizado fora de prazo" - a SEFAZ
     * ainda autoriza o documento com protocolo valido quando o processamento do lote ocorre fora
     * da janela normal (FIS-71); sem isso, o adapter reportava um documento legalmente valido como
     * rejeitado, sem arquivar o XML nem reservar a numeracao. Compartilhado por NF-e/CT-e/MDF-e.
     */
    private static final Set<String> CSTAT_SUCESSO = Set.of("100", "150");

    /**
     * cStat 110/301/302 = "Uso Denegado" (irregularidade fiscal cadastral do emitente ou
     * destinatario) - FIS-100. Diferente de uma rejeicao comum (215/225/... - o cliente corrige o
     * payload e reenvia com o MESMO numero), a SEFAZ ja consome definitivamente aquela chave/numero
     * em caso de denegacao: reemitir com o mesmo numero de serie sera sempre negado de novo. Por
     * isso precisa da propria categoria, distinta de autorizada() e de rejeicao-reenviavel.
     */
    private static final Set<String> CSTAT_DENEGADO = Set.of("110", "301", "302");

    public static AutorizacaoResponse de(String codigoStatus, String motivo, String numeroProtocolo, String dhRecbto) {
        return new AutorizacaoResponse(codigoStatus, motivo, numeroProtocolo, dhRecbto, CSTAT_SUCESSO.contains(codigoStatus));
    }

    public boolean denegada() {
        return CSTAT_DENEGADO.contains(codigoStatus);
    }
}
