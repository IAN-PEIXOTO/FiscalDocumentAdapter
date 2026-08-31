package com.fiscaladapter.sefaz.rejeicao;

/** Como o consumidor da API deve reagir a uma rejeicao/denegacao da SEFAZ (FIS-39). */
public enum CategoriaErroSefaz {

    /** O pedido enviado tem um dado invalido/incompativel - corrigir e reenviar (novo numero, novo XML, etc.). */
    CORRIGIVEL_PELO_CLIENTE,

    /** Falha do lado da SEFAZ (servico indisponivel, documento ainda nao processado) - tentar novamente mais tarde. */
    TRANSITORIO,

    /** Codigo fora do catalogo - o motivo bruto da SEFAZ (sempre presente na resposta) e o unico guia disponivel. */
    DESCONHECIDA
}
