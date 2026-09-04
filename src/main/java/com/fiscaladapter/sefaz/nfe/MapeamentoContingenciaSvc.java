package com.fiscaladapter.sefaz.nfe;

import java.util.Map;

/**
 * Define qual SVC (AN ou RS) atende cada UF em contingencia. Cada UF tem
 * apenas UM SVC oficialmente designado - a outra SVC nao aceita documentos
 * daquela UF.
 *
 * Revalidado (FIS-75): confirmado contra a pagina oficial da SPED-MG
 * (portalsped.fazenda.mg.gov.br/spedmg/nfe/Perguntas-Frequentes/respostas_ix)
 * e contra a declaracao oficial da SEFAZ-PI ("a SEFAZ-PI passou a utilizar a
 * Sefaz Virtual do Rio Grande do Sul") - ambas as fontes concordam entre si
 * e corrigem dois pares que estavam errados na versao anterior desta tabela:
 * PA e PI pertencem a SVC-RS, nao a SVC-AN. PE -> SVC-RS continua confirmado
 * na pagina oficial da SEFAZ-PE, como ja estava. Nao foi possivel acessar
 * diretamente a Nota Tecnica 2013.007 (redirecionamento em loop no portal da
 * NFe) para uma terceira confirmacao primaria; as duas fontes secundarias
 * usadas sao paginas oficiais de orgaos de fazenda estaduais, nao blogs/wikis
 * de terceiros. Total bate: 16 UFs em SVC-AN + 11 em SVC-RS = 27.
 */
public final class MapeamentoContingenciaSvc {

    private static final Map<String, ServicoContingenciaSvc> MAPA = Map.ofEntries(
            Map.entry("AC", ServicoContingenciaSvc.SVC_AN),
            Map.entry("AL", ServicoContingenciaSvc.SVC_AN),
            Map.entry("AP", ServicoContingenciaSvc.SVC_AN),
            Map.entry("DF", ServicoContingenciaSvc.SVC_AN),
            Map.entry("ES", ServicoContingenciaSvc.SVC_AN),
            Map.entry("MG", ServicoContingenciaSvc.SVC_AN),
            Map.entry("PB", ServicoContingenciaSvc.SVC_AN),
            Map.entry("RJ", ServicoContingenciaSvc.SVC_AN),
            Map.entry("RN", ServicoContingenciaSvc.SVC_AN),
            Map.entry("RO", ServicoContingenciaSvc.SVC_AN),
            Map.entry("RR", ServicoContingenciaSvc.SVC_AN),
            Map.entry("RS", ServicoContingenciaSvc.SVC_AN),
            Map.entry("SC", ServicoContingenciaSvc.SVC_AN),
            Map.entry("SE", ServicoContingenciaSvc.SVC_AN),
            Map.entry("SP", ServicoContingenciaSvc.SVC_AN),
            Map.entry("TO", ServicoContingenciaSvc.SVC_AN),
            Map.entry("AM", ServicoContingenciaSvc.SVC_RS),
            Map.entry("BA", ServicoContingenciaSvc.SVC_RS),
            Map.entry("CE", ServicoContingenciaSvc.SVC_RS),
            Map.entry("GO", ServicoContingenciaSvc.SVC_RS),
            Map.entry("MA", ServicoContingenciaSvc.SVC_RS),
            Map.entry("MS", ServicoContingenciaSvc.SVC_RS),
            Map.entry("MT", ServicoContingenciaSvc.SVC_RS),
            Map.entry("PA", ServicoContingenciaSvc.SVC_RS),
            Map.entry("PE", ServicoContingenciaSvc.SVC_RS),
            Map.entry("PI", ServicoContingenciaSvc.SVC_RS),
            Map.entry("PR", ServicoContingenciaSvc.SVC_RS)
    );

    private MapeamentoContingenciaSvc() {
    }

    public static ServicoContingenciaSvc svcPara(String uf) {
        ServicoContingenciaSvc svc = MAPA.get(uf.toUpperCase());
        if (svc == null) {
            throw new IllegalArgumentException("Nao ha SVC de contingencia mapeada para a UF: " + uf);
        }
        return svc;
    }
}
