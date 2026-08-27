package com.fiscaladapter.documento.cte;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatCode;

/** Prova de ponta a ponta do CT-e (FIS-18): gera o XML, assina e valida o resultado final contra o XSD oficial sem nenhum erro. */
class CteEmissaoXmlTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final CteXmlGenerator xmlGenerator = new CteXmlGenerator(chaveAcessoService);
    private final AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();
    private final CteXsdValidator xsdValidator = new CteXsdValidator();

    @Test
    void deveGerarAssinarEValidarOCteCompletoContraOXsdSemNenhumErro() throws Exception {
        Cte cte = CteTestFixture.cteDeExemplo();
        CertificadoCarregado certificado = certificadoDeTeste();

        String chaveAcesso = chaveAcessoService.gerar(cte.identificacao().uf(), cte.identificacao().dataEmissao(),
                cte.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(com.fiscaladapter.documento.TipoDocumentoFiscal.CTE),
                cte.identificacao().serie(), cte.identificacao().numero(), 1);

        String xmlSemAssinatura = xmlGenerator.gerar(cte, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "CTe" + chaveAcesso, certificado);

        assertThatCode(() -> xsdValidator.validar(xmlAssinado)).doesNotThrowAnyException();
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
