package com.fiscaladapter.documento;

import java.util.Map;

/** Codigos numericos de UF (IBGE) usados pela SEFAZ na chave de acesso e nos webservices. */
public final class CodigoUfSefaz {

    private static final Map<String, String> CODIGOS = Map.ofEntries(
            Map.entry("RO", "11"), Map.entry("AC", "12"), Map.entry("AM", "13"), Map.entry("RR", "14"),
            Map.entry("PA", "15"), Map.entry("AP", "16"), Map.entry("TO", "17"), Map.entry("MA", "21"),
            Map.entry("PI", "22"), Map.entry("CE", "23"), Map.entry("RN", "24"), Map.entry("PB", "25"),
            Map.entry("PE", "26"), Map.entry("AL", "27"), Map.entry("SE", "28"), Map.entry("BA", "29"),
            Map.entry("MG", "31"), Map.entry("ES", "32"), Map.entry("RJ", "33"), Map.entry("SP", "35"),
            Map.entry("PR", "41"), Map.entry("SC", "42"), Map.entry("RS", "43"), Map.entry("MS", "50"),
            Map.entry("MT", "51"), Map.entry("GO", "52"), Map.entry("DF", "53")
    );

    private CodigoUfSefaz() {
    }

    public static String codigo(String uf) {
        String codigo = CODIGOS.get(uf.toUpperCase());
        if (codigo == null) {
            throw new IllegalArgumentException("UF desconhecida: " + uf);
        }
        return codigo;
    }

    public static String uf(int codigoNumerico) {
        String codigo = String.valueOf(codigoNumerico);
        return CODIGOS.entrySet().stream()
                .filter(e -> e.getValue().equals(codigo))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Codigo de UF desconhecido: " + codigoNumerico));
    }
}
