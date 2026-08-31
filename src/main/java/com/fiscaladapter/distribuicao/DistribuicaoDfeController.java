package com.fiscaladapter.distribuicao;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consulta de NF-e destinadas a uma empresa, ou seja, emitidas por
 * fornecedores dela - nao pelo proprio CNPJ consultado (FIS-40). Usa o
 * mesmo certificado/autorizacao multi-tenant do FIS-2/FIS-10
 * (CertificadoEmissorService): o CNPJ informado precisa ter certificado
 * cadastrado pelo client_id autenticado, mesmo que ele nunca tenha emitido
 * nenhum documento (so recebido).
 */
@RestController
public class DistribuicaoDfeController {

    private final DistribuicaoDfeService distribuicaoDfeService;
    private final CertificadoEmissorService certificadoEmissorService;

    public DistribuicaoDfeController(DistribuicaoDfeService distribuicaoDfeService,
                                      CertificadoEmissorService certificadoEmissorService) {
        this.distribuicaoDfeService = distribuicaoDfeService;
        this.certificadoEmissorService = certificadoEmissorService;
    }

    @GetMapping("/api/v1/nfe/destinadas")
    public ResponseEntity<List<NfeDestinadaResponse>> destinadas(@RequestParam String cnpjDestinatario,
                                                                   @RequestParam String uf,
                                                                   @RequestParam TipoAmbiente ambiente,
                                                                   Authentication authentication) {
        String cnpj = cnpjDestinatario.replaceAll("\\D", "");
        CertificadoCarregado certificado = certificadoEmissorService.carregar(authentication.getName(), cnpj);

        List<NfeDestinadaResponse> destinadas =
                distribuicaoDfeService.consultarDestinadas(cnpj, uf, ambiente, certificado);
        return ResponseEntity.ok(destinadas);
    }
}
