package com.fiscaladapter.api.nfse;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.nfse.AbrasfNfseClient;
import com.fiscaladapter.sefaz.nfse.CancelamentoNfseResponse;
import com.fiscaladapter.sefaz.nfse.NfseResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * Cancelamento e consulta de status de uma NFS-e ja emitida, junto ao
 * webservice municipal padrao ABRASF (FIS-56) - reaproveita
 * {@link AbrasfNfseClient} (FIS-21), que ja implementa as duas operacoes;
 * faltava so o endpoint REST. Multi-tenant (FIS-10): o certificado e
 * resolvido pelo CPF/CNPJ do prestador informado (nao ha "chave de acesso"
 * na NFS-e para extrair o emissor, diferente de NFe/CT-e/MDF-e).
 *
 * "Mensagem clara quando o municipio nao suportar cancelamento
 * automatizado" (criterio de aceite 3): {@link com.fiscaladapter.sefaz.nfse.NfseEndpointRegistry}
 * ja lanca `IllegalArgumentException` com uma mensagem explicita
 * ("Endpoint de NFS-e nao cadastrado para X - cadastre o endpoint da
 * prefeitura...") quando o municipio nao tem endpoint cadastrado -
 * `GlobalExceptionHandler` traduz isso para HTTP 400, sem precisar de
 * tratamento adicional aqui.
 */
@RestController
public class NfseCancelamentoController {

    /** FIS-57: nenhum destes campos deve conter caracteres especiais de XML - sao concatenados
     * diretamente no envelope SOAP por AbrasfNfseClient (agora escapados na origem tambem, em
     * defesa de profundidade), entao aqui a validacao rejeita cedo com uma mensagem clara. */
    private static final Pattern SOMENTE_DIGITOS = Pattern.compile("^\\d+$");
    private static final Pattern SEM_CARACTERES_XML_ESPECIAIS = Pattern.compile("^[^<>&\"']*$");

    private final AbrasfNfseClient abrasfNfseClient;
    private final CertificadoEmissorService certificadoEmissorService;

    public NfseCancelamentoController(AbrasfNfseClient abrasfNfseClient,
                                       CertificadoEmissorService certificadoEmissorService) {
        this.abrasfNfseClient = abrasfNfseClient;
        this.certificadoEmissorService = certificadoEmissorService;
    }

    /**
     * Cancelamento respeitando o processo especifico do padrao municipal suportado (criterio de
     * aceite 1) - hoje so ABRASF (ver PadraoNfse), unico padrao com cliente de comunicacao
     * implementado (FIS-21).
     */
    @PostMapping("/api/v1/nfse/cancelamento")
    public ResponseEntity<CancelamentoNfseApiResponse> cancelar(@RequestParam String codigoIbgeMunicipio,
                                                                 @RequestParam String numeroNfse,
                                                                 @RequestParam String cpfCnpjPrestador,
                                                                 @RequestParam(required = false) String inscricaoMunicipalPrestador,
                                                                 @RequestParam String codigoMunicipioPrestacao,
                                                                 @RequestParam TipoAmbiente ambiente,
                                                                 Authentication authentication) {
        validarNumerico(numeroNfse, "numeroNfse");
        validarNumerico(codigoMunicipioPrestacao, "codigoMunicipioPrestacao");
        validarSemCaracteresXmlEspeciais(inscricaoMunicipalPrestador, "inscricaoMunicipalPrestador");

        CertificadoCarregado certificado = carregarCertificado(cpfCnpjPrestador, authentication);

        CancelamentoNfseResponse resposta = abrasfNfseClient.cancelarNfse(codigoIbgeMunicipio, numeroNfse,
                cpfCnpjPrestador, inscricaoMunicipalPrestador, codigoMunicipioPrestacao, ambiente, certificado);

        return ResponseEntity.ok(new CancelamentoNfseApiResponse(numeroNfse, resposta.cancelada(),
                resposta.dataHoraCancelamento(), resposta.codigoErro(), resposta.mensagemErro()));
    }

    /** Consulta de status da NFS-e por municipio (criterio de aceite 2), a partir da identificacao do RPS que a originou. */
    @PostMapping("/api/v1/nfse/consulta")
    public ResponseEntity<ConsultaNfseApiResponse> consultar(@RequestParam String codigoIbgeMunicipio,
                                                              @RequestParam long numeroRps,
                                                              @RequestParam String serieRps,
                                                              @RequestParam String cpfCnpjPrestador,
                                                              @RequestParam(required = false) String inscricaoMunicipalPrestador,
                                                              @RequestParam TipoAmbiente ambiente,
                                                              Authentication authentication) {
        validarSemCaracteresXmlEspeciais(serieRps, "serieRps");
        validarSemCaracteresXmlEspeciais(inscricaoMunicipalPrestador, "inscricaoMunicipalPrestador");

        CertificadoCarregado certificado = carregarCertificado(cpfCnpjPrestador, authentication);

        NfseResponse resposta = abrasfNfseClient.consultarNfseRps(codigoIbgeMunicipio, numeroRps, serieRps,
                cpfCnpjPrestador, inscricaoMunicipalPrestador, ambiente, certificado);

        return ResponseEntity.ok(new ConsultaNfseApiResponse(resposta.autorizada(), resposta.numeroNfse(),
                resposta.codigoVerificacao(), resposta.codigoErro(), resposta.mensagemErro()));
    }

    private CertificadoCarregado carregarCertificado(String cpfCnpjPrestador, Authentication authentication) {
        String documento = cpfCnpjPrestador.replaceAll("\\D", "");
        return certificadoEmissorService.carregar(authentication.getName(), documento);
    }

    private void validarNumerico(String valor, String nomeCampo) {
        if (!SOMENTE_DIGITOS.matcher(valor).matches()) {
            throw new IllegalArgumentException(nomeCampo + " deve conter apenas digitos: " + valor);
        }
    }

    private void validarSemCaracteresXmlEspeciais(String valor, String nomeCampo) {
        if (valor != null && !SEM_CARACTERES_XML_ESPECIAIS.matcher(valor).matches()) {
            throw new IllegalArgumentException(nomeCampo + " nao pode conter os caracteres < > & \" '");
        }
    }
}
