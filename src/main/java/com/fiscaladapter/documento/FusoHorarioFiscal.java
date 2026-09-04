package com.fiscaladapter.documento;

import java.time.ZoneId;

/**
 * Fuso horario fixo usado para gerar datas/horas de emissao (dhEmi) nos XMLs enviados a SEFAZ
 * (FIS-84) - NUNCA o fuso do SO/container onde a JVM roda (ZoneId.systemDefault()), que pode
 * divergir do fuso fiscal esperado (ex.: um container com TZ=UTC geraria offset +00:00 em vez do
 * -03:00 esperado para a maioria dos emitentes brasileiros).
 *
 * Simplificacao aceita: usa um unico fuso (Brasilia, -03:00 desde o fim do horario de verao em
 * 2019) para todas as UFs, em vez de mapear o fuso real de cada uma (o Acre e parte do Amazonas
 * usam -05:00). O impacto pratico e pequeno - dhEmi e informativo (a autorizacao real usa dhRecbto
 * gerado pela propria SEFAZ) - mas fixar em vez de depender do SO ja resolve a divergencia mais
 * grave (documentos gerados com offset UTC/errado dependendo de onde a aplicacao roda).
 */
public final class FusoHorarioFiscal {

    public static final ZoneId BRASIL = ZoneId.of("America/Sao_Paulo");

    private FusoHorarioFiscal() {
    }
}
