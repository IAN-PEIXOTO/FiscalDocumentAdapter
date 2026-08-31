package com.fiscaladapter.sefaz.rejeicao;

/**
 * Resultado de classificar uma resposta nao bem-sucedida da SEFAZ (FIS-39).
 *
 * @param codigoStatus cStat bruto devolvido pela SEFAZ - sempre preservado (fallback, criterio de aceite 3).
 * @param motivoBruto  xMotivo bruto devolvido pela SEFAZ - sempre preservado, mesmo quando o codigo esta catalogado.
 * @param mensagem     versao clara e acionavel do erro, ou o proprio motivoBruto quando o codigo nao esta catalogado.
 * @param categoria    indica se vale a pena o cliente corrigir o pedido, tentar novamente, ou nao ha certeza (DESCONHECIDA).
 */
public record RejeicaoSefaz(String codigoStatus, String motivoBruto, String mensagem, CategoriaErroSefaz categoria) {
}
