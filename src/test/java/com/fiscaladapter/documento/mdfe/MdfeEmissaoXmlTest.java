package com.fiscaladapter.documento.mdfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Prova de ponta a ponta do MDF-e (FIS-19): gera o XML, assina e valida o resultado final contra o XSD oficial sem nenhum erro. */
class MdfeEmissaoXmlTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final MdfeXmlGenerator xmlGenerator = new MdfeXmlGenerator(chaveAcessoService);
    private final AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();
    private final MdfeXsdValidator xsdValidator = new MdfeXsdValidator();

    @Test
    void deveGerarAssinarEValidarOMdfeCompletoContraOXsdSemNenhumErro() throws Exception {
        Mdfe mdfe = MdfeTestFixture.mdfeDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        String chaveAcesso = chaveAcessoService.gerar(mdfe.identificacao().uf(), mdfe.identificacao().dataEmissao(),
                mdfe.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.MDFE),
                mdfe.identificacao().serie(), mdfe.identificacao().numero(), 1);

        String xmlSemAssinatura = xmlGenerator.gerar(mdfe, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "MDFe" + chaveAcesso, certificado);

        assertThatCode(() -> xsdValidator.validar(xmlAssinado)).doesNotThrowAnyException();
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
