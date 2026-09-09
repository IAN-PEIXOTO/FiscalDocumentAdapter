package com.fiscaladapter.documento.nfe;

/**
 * Grupo infIntermed (FIS-115): identifica o intermediador da transacao (agenciador, plataforma de
 * delivery, marketplace e similar) quando IdentificacaoNfe.indicadorIntermediador() == "1".
 */
public record Intermediador(String cnpj, String identificadorCadastro) {
}
