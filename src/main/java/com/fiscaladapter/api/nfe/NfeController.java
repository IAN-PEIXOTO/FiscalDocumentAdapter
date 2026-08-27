package com.fiscaladapter.api.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.rvn.RegraNegocioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Endpoint de recebimento de NFe. Nesta fase o certificado do emissor e
 * enviado junto na requisicao (multipart) porque o armazenamento seguro e
 * persistente de certificados ainda esta pendente (ver FIS-2/FIS-14); assim
 * que existir, este endpoint passa a buscar o certificado pelo emissor em
 * vez de recebe-lo a cada chamada.
 */
@RestController
public class NfeController {

    private final NfeRequestMapper mapper;
    private final ChaveAcessoService chaveAcessoService;
    private final NfeXmlGenerator xmlGenerator;
    private final AssinaturaXmlService assinaturaXmlService;
    private final NfeXsdValidator xsdValidator;
    private final CertificadoDigitalService certificadoDigitalService;
    private final RegraNegocioService regraNegocioService;

    public NfeController(NfeRequestMapper mapper, ChaveAcessoService chaveAcessoService,
                          NfeXmlGenerator xmlGenerator, AssinaturaXmlService assinaturaXmlService,
                          NfeXsdValidator xsdValidator, CertificadoDigitalService certificadoDigitalService,
                          RegraNegocioService regraNegocioService) {
        this.mapper = mapper;
        this.chaveAcessoService = chaveAcessoService;
        this.xmlGenerator = xmlGenerator;
        this.assinaturaXmlService = assinaturaXmlService;
        this.xsdValidator = xsdValidator;
        this.certificadoDigitalService = certificadoDigitalService;
        this.regraNegocioService = regraNegocioService;
    }

    @PostMapping(value = "/api/v1/nfe", consumes = "multipart/form-data")
    public ResponseEntity<NfeResponse> emitir(@RequestPart("documento") @Valid NfePedidoEmissaoRequest documento,
                                               @RequestPart("certificado") MultipartFile certificado,
                                               @RequestParam("senhaCertificado") String senhaCertificado) throws IOException {
        NotaFiscalEletronica nfe = mapper.paraDominio(documento);

        regraNegocioService.validar(nfe);

        String chaveAcesso = chaveAcessoService.gerar(
                nfe.identificacao().uf(),
                nfe.identificacao().dataEmissao(),
                nfe.emitente().cnpjSemMascara(),
                chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFE),
                nfe.identificacao().serie(),
                nfe.identificacao().numero(),
                1
        );

        String xmlSemAssinatura = xmlGenerator.gerar(nfe, chaveAcesso);

        CertificadoCarregado certificadoCarregado = certificadoDigitalService.carregar(
                certificado.getInputStream(), senhaCertificado.toCharArray());

        String xmlAssinado = assinaturaXmlService.assinar(
                xmlSemAssinatura, "NFe" + chaveAcesso, certificadoCarregado);

        xsdValidator.validar(xmlAssinado);

        return ResponseEntity.ok(new NfeResponse(chaveAcesso, xmlAssinado));
    }
}
