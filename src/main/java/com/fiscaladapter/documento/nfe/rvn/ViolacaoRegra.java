package com.fiscaladapter.documento.nfe.rvn;

/**
 * Violacao de uma Regra de Validacao de Negocio (RVN), no mesmo formato
 * codigo+mensagem usado nas rejeicoes da SEFAZ - mas com codigos proprios
 * (prefixo RVN-), ja que nao temos acesso a tabela oficial completa de
 * codigos de rejeicao para garantir alinhamento numerico exato (ver FIS-39
 * para o mapeamento de rejeicoes reais vindas da SEFAZ).
 */
public record ViolacaoRegra(String codigo, String mensagem) {
}
