package com.fiscaladapter.sefaz.nfe;

import java.util.Map;

/**
 * Define qual SVC (AN ou RS) atende cada UF em contingencia. Cada UF tem
 * apenas UM SVC oficialmente designado - a outra SVC nao aceita documentos
 * daquela UF.
 *
 * ATENCAO: nao encontrei uma tabela oficial unica e facilmente verificavel
 * para todas as 27 UFs nesta sessao (o portal da NFe nao expos isso de forma
 * simples de consultar, e o INI do ACBr nao contem esse mapeamento - so os
 * enderecos dos SVCs em si). Esta tabela foi montada a partir de fontes
 * secundarias (blogs/wikis de fornecedores fiscais) com apenas um ponto
 * confirmado contra fonte primaria (PE -> SVC-RS, confirmado na pagina
 * oficial da SEFAZ-PE). Os totais batem (18 UFs em SVC-AN + 9 em SVC-RS =
 * 27), o que da alguma confianca estrutural, mas os pares individuais NAO
 * foram todos verificados contra o portal oficial da NFe. Revalidar antes
 * de depender disso em producao.
 */
public final class MapeamentoContingenciaSvc {

    private static final Map<String, ServicoContingenciaSvc> MAPA = Map.ofEntries(
            Map.entry("AC", ServicoContingenciaSvc.SVC_AN),
            Map.entry("AL", ServicoContingenciaSvc.SVC_AN),
            Map.entry("AP", ServicoContingenciaSvc.SVC_AN),
            Map.entry("DF", ServicoContingenciaSvc.SVC_AN),
            Map.entry("ES", ServicoContingenciaSvc.SVC_AN),
            Map.entry("MG", ServicoContingenciaSvc.SVC_AN),
            Map.entry("PA", ServicoContingenciaSvc.SVC_AN),
            Map.entry("PB", ServicoContingenciaSvc.SVC_AN),
            Map.entry("PI", ServicoContingenciaSvc.SVC_AN),
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
            Map.entry("PE", ServicoContingenciaSvc.SVC_RS),
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
