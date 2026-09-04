package com.fiscaladapter.retencao;

import com.fiscaladapter.api.ValidacaoParametros;
import com.fiscaladapter.seguranca.AutorizacaoEmissorService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperacao de documentos fiscais arquivados para retencao legal (FIS-26/34).
 * So o client_id dono do CNPJ emissor (AutorizacaoEmissorService, FIS-10) pode
 * recuperar o XML - mesma regra multi-tenant ja aplicada em certificados e emissao.
 */
@RestController
public class DocumentoFiscalController {

    private final RetencaoDocumentoFiscalService service;
    private final AutorizacaoEmissorService autorizacaoEmissorService;

    public DocumentoFiscalController(RetencaoDocumentoFiscalService service,
                                      AutorizacaoEmissorService autorizacaoEmissorService) {
        this.service = service;
        this.autorizacaoEmissorService = autorizacaoEmissorService;
    }

    @GetMapping("/api/v1/documentos/{chaveAcesso}")
    public ResponseEntity<DocumentoFiscalResponse> recuperar(@PathVariable String chaveAcesso, Authentication authentication) {
        ValidacaoParametros.exigirChaveDeAcessoValida(chaveAcesso);
        return service.recuperar(chaveAcesso)
                .map(documento -> {
                    autorizacaoEmissorService.validarAcesso(authentication.getName(), documento.cnpjEmissor());
                    return ResponseEntity.ok(new DocumentoFiscalResponse(documento.chaveAcesso(),
                            documento.tipoDocumento().name(), documento.numeroProtocolo(),
                            documento.dataEmissao(), documento.xmlAssinado()));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
