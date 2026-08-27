package com.fiscaladapter.documento.nfce;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.security.Signature;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class NfceQrCodeServiceTest {

    private static final String CHAVE_ACESSO_ONLINE = "35260312345678000199650010000000421000000015";
    private static final String CHAVE_ACESSO_OFFLINE = "35260312345678000199650010000000429000000018";

    // padroes oficiais copiados de leiauteNFe_v4.00.xsd (infNFeSupl/qrCode) - ver NfceQrCodeService
    private static final Pattern PADRAO_V3_ONLINE = Pattern.compile(
            "((HTTPS?|https?)://.*\\?p=([0-9]{34}(1|3|4)[0-9]{9})\\|[3]\\|[1-2])");
    private static final Pattern PADRAO_V3_OFFLINE = Pattern.compile(
            "((HTTPS?|https?)://.*\\?p=([0-9]{34}(9)[0-9]{9})\\|[3]\\|[1-2]\\|([0]{1}[1-9]{1}|[1-2]{1}[0-9]{1}|[3]{1}[0-1]{1})\\|"
                    + "(0|0\\.[0-9]{2}|[1-9]{1}[0-9]{0,12}(\\.[0-9]{2})?)\\|((1|2|3)?)\\|(([0-9]{3,14})?)\\|([a-zA-Z0-9+/]+[=]{0,2}))");

    private final NfceQrCodeService service = new NfceQrCodeService();

    @Test
    void deveGerarConteudoOnlineNoFormatoOficialV3() {
        String conteudo = service.gerarConteudoOnline(CHAVE_ACESSO_ONLINE, TipoAmbiente.HOMOLOGACAO,
                "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode");

        assertThat(conteudo).matches(PADRAO_V3_ONLINE);
        assertThat(conteudo).isEqualTo("https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode?p="
                + CHAVE_ACESSO_ONLINE + "|3|2");
    }

    @Test
    void deveAdicionarQuestionPSeUrlNaoTiver() {
        String conteudo = service.gerarConteudoOnline(CHAVE_ACESSO_ONLINE, TipoAmbiente.PRODUCAO, "https://qrcode.exemplo.gov.br/nfce");

        assertThat(conteudo).startsWith("https://qrcode.exemplo.gov.br/nfce?p=");
        assertThat(conteudo).matches(PADRAO_V3_ONLINE);
    }

    @Test
    void deveGerarConteudoOfflineAssinadoNoFormatoOficialV3EVerificavelComAChavePublica() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NotaFiscalEletronica nfce = NotaFiscalEletronicaTestFixture.notaNfceSemDestinatario();

        String conteudo = service.gerarConteudoOffline(nfce, CHAVE_ACESSO_OFFLINE,
                "https://www.homologacao.nfce.fazenda.sp.gov.br/qrcode", certificado);

        assertThat(conteudo).matches(PADRAO_V3_OFFLINE);

        // a "assinatura" e o ultimo campo, separado por "|"; verificamos que bate com a chave publica do certificado
        String semUrl = conteudo.substring(conteudo.indexOf("?p=") + 3);
        int ultimoPipe = semUrl.lastIndexOf('|');
        String semAssinatura = semUrl.substring(0, ultimoPipe);
        String assinaturaBase64 = semUrl.substring(ultimoPipe + 1);

        PublicKey chavePublica = certificado.chaveEEntidade().getCertificate().getPublicKey();
        Signature verificador = Signature.getInstance("SHA1withRSA");
        verificador.initVerify(chavePublica);
        verificador.update(semAssinatura.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(verificador.verify(Base64.getDecoder().decode(assinaturaBase64))).isTrue();
    }

    @Test
    void deveInserirInfNFeSuplAntesDaSignatureSemAlterarOResto() {
        String xmlAssinado = "<NFe><infNFe Id=\"x\">...</infNFe><Signature>...</Signature></NFe>";

        String resultado = service.inserirInfNFeSupl(xmlAssinado, "conteudo-qrcode", "https://exemplo/consulta");

        assertThat(resultado)
                .contains("<infNFeSupl><qrCode>conteudo-qrcode</qrCode><urlChave>https://exemplo/consulta</urlChave></infNFeSupl>")
                .containsSubsequence("</infNFe>", "<infNFeSupl>", "<Signature>");
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
