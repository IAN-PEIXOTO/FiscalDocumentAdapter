package com.fiscaladapter.sefaz.rejeicao;

import java.util.Map;

/**
 * Traduz o cStat bruto devolvido pela SEFAZ para uma mensagem clara e uma
 * indicacao de como o consumidor da API deve agir (FIS-39): corrigir o dado
 * enviado (CORRIGIVEL_PELO_CLIENTE), tentar novamente mais tarde por ser uma
 * falha do lado da SEFAZ (TRANSITORIO), ou nenhuma das duas com certeza
 * (DESCONHECIDA - o motivo bruto, sempre devolvido, e o fallback).
 *
 * Fonte: descricoes oficiais de cStat da NF-e, conferidas contra a tabela
 * publica mantida por nfephp-org/sped-nfe (biblioteca de referencia amplamente
 * usada pela comunidade de integradores NFe brasileira) - nao ha acesso direto
 * ao PDF do MOC (Manual de Orientacao do Contribuinte) da SEFAZ nesta sessao,
 * mas os codigos abaixo sao consistentes entre multiplas fontes independentes
 * consultadas. Cobre os codigos mais frequentes na pratica (criterio de
 * aceite "catalogo dos codigos de rejeicao mais comuns"), nao a tabela
 * completa (que tem centenas de entradas, muitas delas raras ou especificas
 * de CT-e/MDF-e/NFS-e) - qualquer codigo fora desta lista cai no fallback
 * DESCONHECIDA em vez de uma classificacao adivinhada.
 */
public final class CatalogoRejeicaoSefaz {

    private static final Map<String, EntradaCatalogo> CATALOGO = Map.ofEntries(
            // Servico da SEFAZ indisponivel ou documento ainda nao processado - nao e erro do
            // cliente, tentar novamente mais tarde tende a resolver sozinho.
            Map.entry("108", new EntradaCatalogo(
                    "O servico da SEFAZ esta paralisado momentaneamente. Aguarde alguns minutos e tente novamente.",
                    CategoriaErroSefaz.TRANSITORIO)),
            Map.entry("109", new EntradaCatalogo(
                    "O servico da SEFAZ esta paralisado sem previsao de retorno. Tente novamente mais tarde ou acione a contingencia.",
                    CategoriaErroSefaz.TRANSITORIO)),
            Map.entry("217", new EntradaCatalogo(
                    "Esta NF-e ainda nao consta na base de dados da SEFAZ - se acabou de ser emitida, aguarde alguns instantes e consulte novamente.",
                    CategoriaErroSefaz.TRANSITORIO)),

            // Dado enviado invalido/incompativel com o que a SEFAZ espera - o cliente precisa
            // corrigir o pedido (numero, XML, dados cadastrais) e reenviar.
            Map.entry("204", new EntradaCatalogo(
                    "Esta NF-e (mesma chave de acesso) ja foi autorizada anteriormente - nao reenvie o mesmo documento.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("215", new EntradaCatalogo(
                    "O XML gerado nao passou na validacao de schema da SEFAZ - revise os dados enviados no pedido.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("225", new EntradaCatalogo(
                    "O lote enviado nao passou na validacao de schema da SEFAZ - revise os dados enviados no pedido.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("226", new EntradaCatalogo(
                    "A UF do emitente informada nao corresponde a UF que autoriza este documento - confira o campo UF do emitente.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("234", new EntradaCatalogo(
                    "A Inscricao Estadual do destinatario informada nao esta vinculada ao CNPJ dele - confira os dados do destinatario.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("235", new EntradaCatalogo(
                    "A inscricao SUFRAMA informada e invalida - confira o campo SUFRAMA do destinatario.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("241", new EntradaCatalogo(
                    "Este numero de NF-e (nesta serie/CNPJ) ja foi utilizado - use um numero diferente.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("539", new EntradaCatalogo(
                    "Ja existe um documento autorizado com o mesmo numero/serie/CNPJ, mas com chave de acesso diferente - confira se os dados de identificacao nao colidem com um documento ja emitido.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("590", new EntradaCatalogo(
                    "O CST informado nao e compativel com um emitente do Simples Nacional (CRT=1) - confira o Codigo de Regime Tributario e os CSTs usados nos itens.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            // FIS-96: consumo indevido e a propria SEFAZ limitando a taxa de requisicoes do
            // cliente - a acao correta e aguardar e tentar de novo (TRANSITORIO), nao "corrigir"
            // um payload que nao tem nada de errado. Classificar como CORRIGIVEL_PELO_CLIENTE aqui
            // levava o consumidor da API a reenviar sem alterar nada, piorando o throttling.
            Map.entry("656", new EntradaCatalogo(
                    "Consumo indevido detectado pela SEFAZ (excesso de requisicoes) - reduza a frequencia de chamadas antes de tentar novamente.",
                    CategoriaErroSefaz.TRANSITORIO)),

            // Uso denegado por irregularidade fiscal cadastral - nao e um erro no payload em si,
            // mas exige uma acao do emitente/destinatario (fora do escopo desta API) antes de
            // tentar de novo; classificado como corrigivel porque repetir a chamada sem essa
            // acao externa vai falhar sempre com o mesmo motivo.
            Map.entry("110", new EntradaCatalogo(
                    "Uso denegado: ha uma irregularidade fiscal cadastral do emitente ou do destinatario junto a SEFAZ.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("301", new EntradaCatalogo(
                    "Uso denegado: ha uma irregularidade fiscal cadastral do emitente junto a SEFAZ - regularize a situacao antes de tentar novamente.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),
            Map.entry("302", new EntradaCatalogo(
                    "Uso denegado: ha uma irregularidade fiscal cadastral do destinatario junto a SEFAZ - confira o CNPJ/IE do destinatario.",
                    CategoriaErroSefaz.CORRIGIVEL_PELO_CLIENTE)),

            // Fallback generico documentado pela propria SEFAZ - por definicao nunca da para saber
            // mais sem olhar o motivo bruto.
            Map.entry("999", new EntradaCatalogo(
                    "Erro nao catalogado pela SEFAZ - verifique o motivo bruto para mais detalhes.",
                    CategoriaErroSefaz.DESCONHECIDA))
    );

    private CatalogoRejeicaoSefaz() {
    }

    /**
     * @param codigoStatus cStat bruto devolvido pela SEFAZ (ex.: "204").
     * @param motivoBruto  xMotivo bruto devolvido pela SEFAZ - sempre preservado no resultado
     *                     (criterio de aceite 3), mesmo quando o codigo esta catalogado.
     */
    public static RejeicaoSefaz classificar(String codigoStatus, String motivoBruto) {
        EntradaCatalogo entrada = CATALOGO.get(codigoStatus);
        if (entrada == null) {
            return new RejeicaoSefaz(codigoStatus, motivoBruto, motivoBruto, CategoriaErroSefaz.DESCONHECIDA);
        }
        return new RejeicaoSefaz(codigoStatus, motivoBruto, entrada.mensagem(), entrada.categoria());
    }

    private record EntradaCatalogo(String mensagem, CategoriaErroSefaz categoria) {
    }
}
