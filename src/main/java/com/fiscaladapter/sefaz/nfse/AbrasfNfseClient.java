package com.fiscaladapter.sefaz.nfse;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazComunicacaoException;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente de comunicacao com os webservices municipais de NFS-e, padrao
 * ABRASF (FIS-21): geracao (envio do RPS ja gerado pelo FIS-20), consulta e
 * cancelamento. Separado dos clientes da SEFAZ estadual (pacote sefaz.nfe)
 * porque o endpoint, a autenticacao e, as vezes, ate pequenas variacoes do
 * schema mudam por prefeitura - ver NfseEndpointRegistry.
 *
 * A assinatura digital do RPS e OPCIONAL no XSD da ABRASF (diferente de
 * NFe/CTe/MDFe, onde e obrigatoria) - cada prefeitura decide se exige. Por
 * isso este cliente recebe o XML do RPS pronto (assinado ou nao, decisao de
 * quem chama) em vez de assinar aqui dentro.
 */
@Component
public class AbrasfNfseClient {

    private static final Pattern TAG_NUMERO = Pattern.compile("<Numero>(\\d+)</Numero>");
    private static final Pattern TAG_CODIGO_VERIFICACAO = Pattern.compile("<CodigoVerificacao>(.*?)</CodigoVerificacao>");
    private static final Pattern TAG_MENSAGEM_RETORNO = Pattern.compile("<MensagemRetorno>.*?</MensagemRetorno>", Pattern.DOTALL);
    private static final Pattern TAG_CODIGO_ERRO = Pattern.compile("<Codigo>(.*?)</Codigo>");
    private static final Pattern TAG_MENSAGEM_ERRO = Pattern.compile("<Mensagem>(.*?)</Mensagem>");
    private static final Pattern TAG_DATA_HORA = Pattern.compile("<DataHora>(.*?)</DataHora>");

    private final NfseEndpointRegistry endpointRegistry;
    private final SefazHttpClientFactory httpClientFactory;

    public AbrasfNfseClient(NfseEndpointRegistry endpointRegistry, SefazHttpClientFactory httpClientFactory) {
        this.endpointRegistry = endpointRegistry;
        this.httpClientFactory = httpClientFactory;
    }

    public NfseResponse gerarNfse(String codigoIbgeMunicipio, String rpsXml, TipoAmbiente ambiente,
                                   CertificadoCarregado certificado) {
        return gerarNfse(codigoIbgeMunicipio, rpsXml, ambiente, httpClientFactory.criar(certificado));
    }

    NfseResponse gerarNfse(String codigoIbgeMunicipio, String rpsXml, TipoAmbiente ambiente, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(codigoIbgeMunicipio, ambiente, TipoServicoAbrasfNfse.GERAR_NFSE);
        return gerarNfseNoEndpoint(url, rpsXml, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do webservice real da prefeitura. */
    NfseResponse gerarNfseNoEndpoint(String url, String rpsXml, HttpClient httpClient) {
        String resposta = AbrasfSoapClient.enviar(httpClient, url, "GerarNfse", rpsXml);
        return interpretarRespostaNfse(resposta);
    }

    public NfseResponse consultarNfseRps(String codigoIbgeMunicipio, long numeroRps, String serieRps,
                                          String cpfCnpjPrestador, String inscricaoMunicipalPrestador,
                                          TipoAmbiente ambiente, CertificadoCarregado certificado) {
        return consultarNfseRps(codigoIbgeMunicipio, numeroRps, serieRps, cpfCnpjPrestador, inscricaoMunicipalPrestador,
                ambiente, httpClientFactory.criar(certificado));
    }

    NfseResponse consultarNfseRps(String codigoIbgeMunicipio, long numeroRps, String serieRps, String cpfCnpjPrestador,
                                   String inscricaoMunicipalPrestador, TipoAmbiente ambiente, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(codigoIbgeMunicipio, ambiente, TipoServicoAbrasfNfse.CONSULTAR_NFSE_RPS);
        return consultarNfseRpsNoEndpoint(url, numeroRps, serieRps, cpfCnpjPrestador, inscricaoMunicipalPrestador, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do webservice real da prefeitura. */
    NfseResponse consultarNfseRpsNoEndpoint(String url, long numeroRps, String serieRps, String cpfCnpjPrestador,
                                             String inscricaoMunicipalPrestador, HttpClient httpClient) {
        String documento = cpfCnpjPrestador.replaceAll("\\D", "");
        String tagDocumento = documento.length() == 14 ? "Cnpj" : "Cpf";

        String xml = "<ConsultarNfseRpsEnvio xmlns=\"http://www.abrasf.org.br/nfse.xsd\">"
                + "<IdentificacaoRps>"
                + "<Numero>" + numeroRps + "</Numero>"
                + "<Serie>" + escaparXml(serieRps) + "</Serie>"
                + "<Tipo>1</Tipo>"
                + "</IdentificacaoRps>"
                + "<Prestador>"
                + "<CpfCnpj><" + tagDocumento + ">" + documento + "</" + tagDocumento + "></CpfCnpj>"
                + (inscricaoMunicipalPrestador != null ? "<InscricaoMunicipal>" + escaparXml(inscricaoMunicipalPrestador) + "</InscricaoMunicipal>" : "")
                + "</Prestador>"
                + "</ConsultarNfseRpsEnvio>";

        String resposta = AbrasfSoapClient.enviar(httpClient, url, "ConsultarNfseRps", xml);
        return interpretarRespostaNfse(resposta);
    }

    public CancelamentoNfseResponse cancelarNfse(String codigoIbgeMunicipio, String numeroNfse, String cpfCnpjPrestador,
                                                  String inscricaoMunicipalPrestador, String codigoMunicipioPrestacao,
                                                  TipoAmbiente ambiente, CertificadoCarregado certificado) {
        return cancelarNfse(codigoIbgeMunicipio, numeroNfse, cpfCnpjPrestador, inscricaoMunicipalPrestador,
                codigoMunicipioPrestacao, ambiente, httpClientFactory.criar(certificado));
    }

    CancelamentoNfseResponse cancelarNfse(String codigoIbgeMunicipio, String numeroNfse, String cpfCnpjPrestador,
                                           String inscricaoMunicipalPrestador, String codigoMunicipioPrestacao,
                                           TipoAmbiente ambiente, HttpClient httpClient) {
        String url = endpointRegistry.obterUrl(codigoIbgeMunicipio, ambiente, TipoServicoAbrasfNfse.CANCELAR_NFSE);
        return cancelarNfseNoEndpoint(url, numeroNfse, cpfCnpjPrestador, inscricaoMunicipalPrestador, codigoMunicipioPrestacao, httpClient);
    }

    /** Visivel para testes: permite apontar para um servidor de teste local em vez do webservice real da prefeitura. */
    CancelamentoNfseResponse cancelarNfseNoEndpoint(String url, String numeroNfse, String cpfCnpjPrestador,
                                                     String inscricaoMunicipalPrestador, String codigoMunicipioPrestacao,
                                                     HttpClient httpClient) {
        String documento = cpfCnpjPrestador.replaceAll("\\D", "");
        String tagDocumento = documento.length() == 14 ? "Cnpj" : "Cpf";

        String xml = "<CancelarNfseEnvio xmlns=\"http://www.abrasf.org.br/nfse.xsd\">"
                + "<Pedido>"
                + "<InfPedidoCancelamento>"
                + "<IdentificacaoNfse>"
                + "<Numero>" + escaparXml(numeroNfse) + "</Numero>"
                + "<CpfCnpj><" + tagDocumento + ">" + documento + "</" + tagDocumento + "></CpfCnpj>"
                + (inscricaoMunicipalPrestador != null ? "<InscricaoMunicipal>" + escaparXml(inscricaoMunicipalPrestador) + "</InscricaoMunicipal>" : "")
                + "<CodigoMunicipio>" + escaparXml(codigoMunicipioPrestacao) + "</CodigoMunicipio>"
                + "</IdentificacaoNfse>"
                + "</InfPedidoCancelamento>"
                + "</Pedido>"
                + "</CancelarNfseEnvio>";

        String resposta = AbrasfSoapClient.enviar(httpClient, url, "CancelarNfse", xml);
        return interpretarRespostaCancelamento(resposta);
    }

    private NfseResponse interpretarRespostaNfse(String respostaXml) {
        Matcher matcherNumero = TAG_NUMERO.matcher(respostaXml);
        Matcher matcherCodigoVerificacao = TAG_CODIGO_VERIFICACAO.matcher(respostaXml);
        if (matcherNumero.find() && matcherCodigoVerificacao.find()) {
            return NfseResponse.sucesso(matcherNumero.group(1), matcherCodigoVerificacao.group(1));
        }

        Matcher matcherMensagemRetorno = TAG_MENSAGEM_RETORNO.matcher(respostaXml);
        if (matcherMensagemRetorno.find()) {
            String bloco = matcherMensagemRetorno.group();
            return NfseResponse.erro(extrair(TAG_CODIGO_ERRO, bloco), extrair(TAG_MENSAGEM_ERRO, bloco));
        }

        throw new SefazComunicacaoException("Resposta do webservice de NFS-e nao reconhecida: " + respostaXml);
    }

    private CancelamentoNfseResponse interpretarRespostaCancelamento(String respostaXml) {
        Matcher matcherDataHora = TAG_DATA_HORA.matcher(respostaXml);
        if (matcherDataHora.find()) {
            return CancelamentoNfseResponse.sucesso(matcherDataHora.group(1));
        }

        Matcher matcherMensagemRetorno = TAG_MENSAGEM_RETORNO.matcher(respostaXml);
        if (matcherMensagemRetorno.find()) {
            String bloco = matcherMensagemRetorno.group();
            return CancelamentoNfseResponse.erro(extrair(TAG_CODIGO_ERRO, bloco), extrair(TAG_MENSAGEM_ERRO, bloco));
        }

        throw new SefazComunicacaoException("Resposta do webservice de NFS-e nao reconhecida: " + respostaXml);
    }

    private String extrair(Pattern padrao, String texto) {
        Matcher matcher = padrao.matcher(texto);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Escapa os caracteres especiais de XML (FIS-57) - numero do RPS/NFS-e, serie, inscricao
     * municipal e codigo do municipio sao concatenados diretamente na string do envelope (esta
     * classe monta o XML na mao, sem um writer que escape automaticamente como o
     * XMLStreamWriter usado no XML fiscal principal); sem isso, um valor como
     * "</Numero><Malicioso>" quebraria/adulteraria a estrutura do XML enviado a prefeitura.
     */
    private static String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
