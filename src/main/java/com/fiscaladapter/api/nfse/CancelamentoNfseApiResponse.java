package com.fiscaladapter.api.nfse;

/**
 * codigoErro/mensagemErro vem nulos quando cancelada=true. Diferente da NFe/CT-e/MDF-e, aqui nao
 * ha um catalogo de rejeicao (CatalogoRejeicaoSefaz) - os codigos de erro da ABRASF variam por
 * prefeitura (nao ha uma tabela nacional unica de motivos), entao o codigo/mensagem crus devolvidos
 * pelo webservice municipal (FIS-21) sao repassados como estao.
 */
public record CancelamentoNfseApiResponse(
        String numeroNfse,
        boolean cancelada,
        String dataHoraCancelamento,
        String codigoErro,
        String mensagemErro
) {
}
