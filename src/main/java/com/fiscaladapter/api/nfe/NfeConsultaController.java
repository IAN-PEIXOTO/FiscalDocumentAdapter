package com.fiscaladapter.api.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.CceResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.sefaz.nfe.InutilizacaoResponse;
import com.fiscaladapter.sefaz.nfe.ManifestacaoResponse;
import com.fiscaladapter.sefaz.nfe.NfeCancelamentoClient;
import com.fiscaladapter.sefaz.nfe.NfeCceClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
import com.fiscaladapter.sefaz.nfe.NfeInutilizacaoClient;
import com.fiscaladapter.sefaz.nfe.NfeManifestacaoDestinatarioClient;
import com.fiscaladapter.sefaz.nfe.TipoManifestacaoDestinatario;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints pos-emissao de NFe: consulta de situacao, cancelamento e carta de
 * correcao (FIS-51/FIS-55). O certificado e resolvido pelo CNPJ do emitente,
 * extraido da propria chave de acesso (FIS-2) - nao precisa mais ser
 * reenviado a cada chamada.
 */
@RestController
public class NfeConsultaController {

    private final NfeConsultaProtocoloClient consultaProtocoloClient;
    private final NfeCancelamentoClient cancelamentoClient;
    private final NfeCceClient cceClient;
    private final NfeInutilizacaoClient inutilizacaoClient;
    private final NfeManifestacaoDestinatarioClient manifestacaoDestinatarioClient;
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;

    public NfeConsultaController(NfeConsultaProtocoloClient consultaProtocoloClient,
                                  NfeCancelamentoClient cancelamentoClient,
                                  NfeCceClient cceClient,
                                  NfeInutilizacaoClient inutilizacaoClient,
                                  NfeManifestacaoDestinatarioClient manifestacaoDestinatarioClient,
                                  CertificadoEmissorService certificadoEmissorService,
                                  ChaveAcessoService chaveAcessoService) {
        this.consultaProtocoloClient = consultaProtocoloClient;
        this.cancelamentoClient = cancelamentoClient;
        this.cceClient = cceClient;
        this.inutilizacaoClient = inutilizacaoClient;
        this.manifestacaoDestinatarioClient = manifestacaoDestinatarioClient;
        this.certificadoEmissorService = certificadoEmissorService;
        this.chaveAcessoService = chaveAcessoService;
    }

    @PostMapping("/api/v1/nfe/{chaveAcesso}/consulta")
    public ResponseEntity<ConsultaNfeResponse> consultar(@PathVariable String chaveAcesso,
                                                           @RequestParam String uf,
                                                           @RequestParam TipoAmbiente ambiente) {
        CertificadoCarregado certificadoCarregado = carregarCertificado(chaveAcesso);

        ConsultaProtocoloResponse resposta = consultaProtocoloClient.consultar(chaveAcesso, uf, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new ConsultaNfeResponse(
                chaveAcesso, resposta.autorizada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo()));
    }

    @PostMapping("/api/v1/nfe/{chaveAcesso}/cancelamento")
    public ResponseEntity<CancelamentoNfeResponse> cancelar(@PathVariable String chaveAcesso,
                                                              @RequestParam String uf,
                                                              @RequestParam TipoAmbiente ambiente,
                                                              @RequestParam String numeroProtocolo,
                                                              @RequestParam String justificativa) {
        CertificadoCarregado certificadoCarregado = carregarCertificado(chaveAcesso);

        CancelamentoResponse resposta = cancelamentoClient.cancelar(
                chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new CancelamentoNfeResponse(
                chaveAcesso, resposta.cancelado(), resposta.codigoStatus(), resposta.motivo()));
    }

    @PostMapping("/api/v1/nfe/{chaveAcesso}/cartaCorrecao")
    public ResponseEntity<CceNfeResponse> corrigir(@PathVariable String chaveAcesso,
                                                     @RequestParam String uf,
                                                     @RequestParam TipoAmbiente ambiente,
                                                     @RequestParam int numeroSequencial,
                                                     @RequestParam String textoCorrecao) {
        CertificadoCarregado certificadoCarregado = carregarCertificado(chaveAcesso);

        CceResponse resposta = cceClient.corrigir(
                chaveAcesso, numeroSequencial, textoCorrecao, uf, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new CceNfeResponse(
                chaveAcesso, resposta.registrada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo()));
    }

    /**
     * Inutiliza uma faixa de numeracao que nao sera usada (numeros pulados por erro
     * de sistema, nota cancelada antes da transmissao, etc.) - obrigacao legal, nao
     * associada a uma chave de acesso ja emitida (ela nunca existiu).
     */
    @PostMapping("/api/v1/nfe/inutilizacao")
    public ResponseEntity<InutilizacaoNfeResponse> inutilizar(@RequestParam String uf,
                                                                 @RequestParam TipoAmbiente ambiente,
                                                                 @RequestParam String cnpjEmitente,
                                                                 @RequestParam int serie,
                                                                 @RequestParam long numeroInicial,
                                                                 @RequestParam long numeroFinal,
                                                                 @RequestParam String justificativa) {
        CertificadoCarregado certificadoCarregado = certificadoEmissorService.carregar(cnpjEmitente.replaceAll("\\D", ""));

        InutilizacaoResponse resposta = inutilizacaoClient.inutilizar(
                cnpjEmitente, uf, serie, numeroInicial, numeroFinal, justificativa, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new InutilizacaoNfeResponse(
                resposta.inutilizada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo()));
    }

    /**
     * Manifestacao do Destinatario (FIS-9/FIS-40): quem manifesta e o
     * DESTINATARIO da NFe, usando o certificado do seu proprio CNPJ (nao o do
     * emitente) - por isso cnpjManifestante e explicito e nao derivado da
     * chave de acesso, que so contem o CNPJ do emitente.
     */
    @PostMapping("/api/v1/nfe/{chaveAcesso}/manifestacao")
    public ResponseEntity<ManifestacaoNfeResponse> manifestar(@PathVariable String chaveAcesso,
                                                                @RequestParam TipoAmbiente ambiente,
                                                                @RequestParam String cnpjManifestante,
                                                                @RequestParam TipoManifestacaoDestinatario tipo,
                                                                @RequestParam(required = false) String justificativa) {
        CertificadoCarregado certificadoCarregado =
                certificadoEmissorService.carregar(cnpjManifestante.replaceAll("\\D", ""));

        ManifestacaoResponse resposta = manifestacaoDestinatarioClient.manifestar(
                chaveAcesso, tipo, justificativa, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new ManifestacaoNfeResponse(
                chaveAcesso, resposta.registrada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo()));
    }

    private CertificadoCarregado carregarCertificado(String chaveAcesso) {
        return certificadoEmissorService.carregar(chaveAcessoService.cnpjEmitente(chaveAcesso));
    }
}
