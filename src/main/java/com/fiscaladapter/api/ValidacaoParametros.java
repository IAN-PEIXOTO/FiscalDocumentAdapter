package com.fiscaladapter.api;

import java.util.regex.Pattern;

/**
 * Validacao de parametros de entrada compartilhada entre controllers (FIS-58) - usada para
 * `@RequestParam` que sao concatenados diretamente em XML montado na mao (sem um writer que
 * escape automaticamente), como `numeroProtocolo`/`codigoMunicipioEncerramento` nos eventos de
 * cancelamento/encerramento de NFe/CT-e/MDF-e. O escape na origem (nos clientes SOAP) ja elimina
 * a injecao independente do valor recebido; esta validacao e uma segunda camada, rejeitando cedo
 * com uma mensagem clara em vez de deixar o valor malformado seguir ate o cliente SOAP.
 */
public final class ValidacaoParametros {

    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("^\\d+$");

    private ValidacaoParametros() {
    }

    public static void exigirSomenteDigitos(String valor, String nomeCampo) {
        if (valor == null || !SOMENTE_DIGITOS.matcher(valor).matches()) {
            throw new IllegalArgumentException(nomeCampo + " deve conter apenas digitos: " + valor);
        }
    }
}
