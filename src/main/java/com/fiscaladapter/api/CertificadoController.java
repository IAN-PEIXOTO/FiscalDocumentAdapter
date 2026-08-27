package com.fiscaladapter.api;

import com.fiscaladapter.certificado.CertificadoEmissorService;
import com.fiscaladapter.certificado.CertificadoInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/**
 * Registro do certificado A1 de um emissor (FIS-2): uma vez cadastrado aqui,
 * as emissoes de NFe (POST /api/v1/nfe) resolvem o certificado automaticamente
 * pelo CNPJ do emissor no proprio payload, sem precisar reenviar o .p12 a
 * cada chamada.
 */
@RestController
public class CertificadoController {

    private final CertificadoEmissorService certificadoEmissorService;

    public CertificadoController(CertificadoEmissorService certificadoEmissorService) {
        this.certificadoEmissorService = certificadoEmissorService;
    }

    @PostMapping(value = "/api/v1/certificados", consumes = "multipart/form-data")
    public ResponseEntity<CertificadoRegistradoResponse> registrar(@RequestPart("certificado") MultipartFile certificado,
                                                                     @RequestParam("senhaCertificado") String senhaCertificado) {
        char[] senha = senhaCertificado.toCharArray();
        try {
            byte[] arquivoP12 = certificado.getBytes();
            CertificadoInfo info = certificadoEmissorService.registrar(arquivoP12, senha);
            return ResponseEntity.ok(new CertificadoRegistradoResponse(
                    info.cnpj(), info.subjectDn(), info.validoDe(), info.validoAte()));
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o arquivo de certificado enviado", e);
        } finally {
            Arrays.fill(senha, '\0');
        }
    }

    @DeleteMapping("/api/v1/certificados/{cnpj}")
    public ResponseEntity<Void> remover(@PathVariable String cnpj) {
        certificadoEmissorService.remover(cnpj);
        return ResponseEntity.noContent().build();
    }
}
