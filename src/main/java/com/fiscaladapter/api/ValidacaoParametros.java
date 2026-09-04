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
    private static final Pattern CHAVE_DE_ACESSO = Pattern.compile("^\\d{44}$");

    private ValidacaoParametros() {
    }

    public static void exigirSomenteDigitos(String valor, String nomeCampo) {
        if (valor == null || !SOMENTE_DIGITOS.matcher(valor).matches()) {
            throw new IllegalArgumentException(nomeCampo + " deve conter apenas digitos: " + valor);
        }
    }

    /**
     * Valida o formato da chave de acesso (44 digitos numericos) ANTES de qualquer
     * processamento (FIS-59) - sem isso, `ChaveAcessoService.cnpjEmitente`/`modeloDocumento` (e o
     * `substring(0,2)` inline nos clientes de consulta de CT-e/MDF-e) fazem `substring` fixo sem
     * checar tamanho, lancando `StringIndexOutOfBoundsException` para uma chave curta/vazia - essa
     * excecao cai no handler generico do GlobalExceptionHandler e vira HTTP 500 (logado como erro
     * de infraestrutura), quando o problema real e so uma entrada malformada do cliente (HTTP 400).
     */
    public static void exigirChaveDeAcessoValida(String chaveAcesso) {
        if (chaveAcesso == null || !CHAVE_DE_ACESSO.matcher(chaveAcesso).matches()) {
            throw new IllegalArgumentException("chaveAcesso deve conter exatamente 44 digitos numericos: " + chaveAcesso);
        }
    }
}
