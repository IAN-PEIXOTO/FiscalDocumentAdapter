package com.fiscaladapter.documento.nfce;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.TipoDocumentoFiscal;
import com.fiscaladapter.documento.nfe.ChaveAcessoService;
import com.fiscaladapter.documento.nfe.NfeXmlGenerator;
import com.fiscaladapter.documento.nfe.NfeXsdValidator;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Prova de ponta a ponta da geracao da NFC-e (FIS-17): gera o XML (sem
 * destinatario, consumidor nao identificado), assina, insere o QR Code
 * (online e offline) e valida o resultado final contra o XSD oficial - com
 * QR Code inserido, o unico "erro" que os outros testes de NFe toleram
 * (Signature ausente) deixa de existir, entao aqui a validacao precisa
 * passar 100% limpa.
 */
class NfceEmissaoXmlTest {

    private final ChaveAcessoService chaveAcessoService = new ChaveAcessoService();
    private final NfeXmlGenerator xmlGenerator = new NfeXmlGenerator(chaveAcessoService);
    private final AssinaturaXmlService assinaturaXmlService = new AssinaturaXmlService();
    private final NfeXsdValidator xsdValidator = new NfeXsdValidator();
    private final NfceQrCodeService qrCodeService = new NfceQrCodeService();

    @Test
    void deveGerarNfceComQrCodeOnlineEValidarContraOXsdSemNenhumErro() throws Exception {
        NotaFiscalEletronica nfce = NotaFiscalEletronicaTestFixture.notaNfceSemDestinatario();
        CertificadoCarregado certificado = certificadoDeTeste();

        String chaveAcesso = chaveAcessoService.gerar(nfce.identificacao().uf(), nfce.identificacao().dataEmissao(),
                nfce.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFCE),
                nfce.identificacao().serie(), nfce.identificacao().numero(), 1);

        String xmlSemAssinatura = xmlGenerator.gerar(nfce, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "NFe" + chaveAcesso, certificado);

        String conteudoQr = qrCodeService.gerarConteudoOnline(chaveAcesso, TipoAmbiente.HOMOLOGACAO,
                "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode");
        String urlConsultaPorChave = "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta";
        String xmlFinal = qrCodeService.inserirInfNFeSupl(xmlAssinado, conteudoQr, urlConsultaPorChave);

        assertThatCode(() -> xsdValidator.validar(xmlFinal)).doesNotThrowAnyException();
    }

    @Test
    void deveGerarNfceComQrCodeOfflineDeContingenciaEValidarContraOXsdSemNenhumErro() throws Exception {
        NotaFiscalEletronica nfce = NotaFiscalEletronicaTestFixture.notaNfceSemDestinatario();
        CertificadoCarregado certificado = certificadoDeTeste();

        // tpEmis=9: contingencia offline especifica da NFC-e (FIS-17), nao usa SVC-AN/SVC-RS
        String chaveAcesso = chaveAcessoService.gerar(nfce.identificacao().uf(), nfce.identificacao().dataEmissao(),
                nfce.emitente().cnpjSemMascara(), chaveAcessoService.modeloPara(TipoDocumentoFiscal.NFCE),
                nfce.identificacao().serie(), nfce.identificacao().numero(), 9);

        String xmlSemAssinatura = xmlGenerator.gerar(nfce, chaveAcesso);
        String xmlAssinado = assinaturaXmlService.assinar(xmlSemAssinatura, "NFe" + chaveAcesso, certificado);

        String conteudoQr = qrCodeService.gerarConteudoOffline(nfce, chaveAcesso,
                "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode", certificado);
        String urlConsultaPorChave = "https://www.homologacao.nfce.fazenda.sp.gov.br/consulta";
        String xmlFinal = qrCodeService.inserirInfNFeSupl(xmlAssinado, conteudoQr, urlConsultaPorChave);

        assertThatCode(() -> xsdValidator.validar(xmlFinal)).doesNotThrowAnyException();
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
