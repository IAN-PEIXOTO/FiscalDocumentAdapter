package com.fiscaladapter.api;

import java.util.List;

public record ErroResposta(String mensagem, List<String> detalhes) {

    public ErroResposta(String mensagem) {
        this(mensagem, List.of());
    }
}
