package com.fiscaladapter.api.nfe;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Endpoints pos-emissao de NFe: consulta de situacao e cancelamento
 * (FIS-51). O certificado ainda e enviado a cada chamada (multipart), pelo
 * mesmo motivo do NfeController - armazenamento persistente de certificados
 * e o FIS-2/FIS-14, ainda pendente.
 */
@RestController
public class NfeConsultaController {

    private final NfeConsultaProtocoloClient consultaProtocoloClient;
    private final NfeCancelamentoClient cancelamentoClient;
    private final NfeCceClient cceClient;
    private final CertificadoDigitalService certificadoDigitalService;

    public NfeConsultaController(NfeConsultaProtocoloClient consultaProtocoloClient,
                                  NfeCancelamentoClient cancelamentoClient,
                                  NfeCceClient cceClient,
                                  CertificadoDigitalService certificadoDigitalService) {
        this.consultaProtocoloClient = consultaProtocoloClient;
        this.cancelamentoClient = cancelamentoClient;
        this.cceClient = cceClient;
        this.certificadoDigitalService = certificadoDigitalService;
    }

    @PostMapping(value = "/api/v1/nfe/{chaveAcesso}/consulta", consumes = "multipart/form-data")
    public ResponseEntity<ConsultaNfeResponse> consultar(@PathVariable String chaveAcesso,
                                                           @RequestParam String uf,
                                                           @RequestParam TipoAmbiente ambiente,
                                                           @RequestPart("certificado") MultipartFile certificado,
                                                           @RequestParam String senhaCertificado) {
        CertificadoCarregado certificadoCarregado = carregarCertificado(certificado, senhaCertificado);

        ConsultaProtocoloResponse resposta = consultaProtocoloClient.consultar(chaveAcesso, uf, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new ConsultaNfeResponse(
                chaveAcesso, resposta.autorizada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo()));
    }

    @PostMapping(value = "/api/v1/nfe/{chaveAcesso}/cancelamento", consumes = "multipart/form-data")
    public ResponseEntity<CancelamentoNfeResponse> cancelar(@PathVariable String chaveAcesso,
                                                              @RequestParam String uf,
                                                              @RequestParam TipoAmbiente ambiente,
                                                              @RequestParam String numeroProtocolo,
                                                              @RequestParam String justificativa,
                                                              @RequestPart("certificado") MultipartFile certificado,
                                                              @RequestParam String senhaCertificado) {
        CertificadoCarregado certificadoCarregado = carregarCertificado(certificado, senhaCertificado);

        CancelamentoResponse resposta = cancelamentoClient.cancelar(
                chaveAcesso, numeroProtocolo, justificativa, uf, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new CancelamentoNfeResponse(
                chaveAcesso, resposta.cancelado(), resposta.codigoStatus(), resposta.motivo()));
    }

    @PostMapping(value = "/api/v1/nfe/{chaveAcesso}/cartaCorrecao", consumes = "multipart/form-data")
    public ResponseEntity<CceNfeResponse> corrigir(@PathVariable String chaveAcesso,
                                                     @RequestParam String uf,
                                                     @RequestParam TipoAmbiente ambiente,
                                                     @RequestParam int numeroSequencial,
                                                     @RequestParam String textoCorrecao,
                                                     @RequestPart("certificado") MultipartFile certificado,
                                                     @RequestParam String senhaCertificado) {
        CertificadoCarregado certificadoCarregado = carregarCertificado(certificado, senhaCertificado);

        CceResponse resposta = cceClient.corrigir(
                chaveAcesso, numeroSequencial, textoCorrecao, uf, ambiente, certificadoCarregado);

        return ResponseEntity.ok(new CceNfeResponse(
                chaveAcesso, resposta.registrada(), resposta.codigoStatus(), resposta.motivo(), resposta.numeroProtocolo()));
    }

    private CertificadoCarregado carregarCertificado(MultipartFile certificado, String senha) {
        try {
            return certificadoDigitalService.carregar(certificado.getInputStream(), senha.toCharArray());
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o arquivo de certificado enviado", e);
        }
    }
}
