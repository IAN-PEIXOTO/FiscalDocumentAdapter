package com.fiscaladapter.api.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.nfe.CancelamentoResponse;
import com.fiscaladapter.sefaz.nfe.CceResponse;
import com.fiscaladapter.sefaz.nfe.ConsultaProtocoloResponse;
import com.fiscaladapter.sefaz.nfe.NfeCancelamentoClient;
import com.fiscaladapter.sefaz.nfe.NfeCceClient;
import com.fiscaladapter.sefaz.nfe.NfeConsultaProtocoloClient;
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
    private final CertificadoEmissorService certificadoEmissorService;
    private final ChaveAcessoService chaveAcessoService;

    public NfeConsultaController(NfeConsultaProtocoloClient consultaProtocoloClient,
                                  NfeCancelamentoClient cancelamentoClient,
                                  NfeCceClient cceClient,
                                  CertificadoEmissorService certificadoEmissorService,
                                  ChaveAcessoService chaveAcessoService) {
        this.consultaProtocoloClient = consultaProtocoloClient;
        this.cancelamentoClient = cancelamentoClient;
        this.cceClient = cceClient;
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

    private CertificadoCarregado carregarCertificado(String chaveAcesso) {
        return certificadoEmissorService.carregar(chaveAcessoService.cnpjEmitente(chaveAcesso));
    }
}
