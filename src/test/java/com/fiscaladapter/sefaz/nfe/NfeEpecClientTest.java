package com.fiscaladapter.sefaz.nfe;

import com.fiscaladapter.assinatura.AssinaturaXmlService;
import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.certificado.CertificadoDigitalService;
import com.fiscaladapter.certificado.TestCertificadoFactory;
import com.fiscaladapter.documento.nfe.Destinatario;
import com.fiscaladapter.documento.nfe.Emitente;
import com.fiscaladapter.documento.nfe.Endereco;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronicaTestFixture;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import com.fiscaladapter.sefaz.SefazHttpClientFactory;
import com.fiscaladapter.sefaz.ServidorSoapDeTeste;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class NfeEpecClientTest {

    private static final String CHAVE_ACESSO = "35260412345678000199550010000000424000000015";

    private static final String RESPOSTA_EPEC_REGISTRADO =
            "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\">"
                    + "<soap:Body><nfeResultMsg xmlns=\"http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4\">"
                    + "<retEnvEvento versao=\"1.00\" xmlns=\"http://www.portalfiscal.inf.br/nfe\">"
                    + "<idLote>1</idLote><tpAmb>2</tpAmb><verAplic>RS1.0</verAplic><cOrgao>91</cOrgao>"
                    + "<cStat>128</cStat><xMotivo>Lote de Evento Processado</xMotivo>"
                    + "<retEvento versao=\"1.00\"><infEvento><tpAmb>2</tpAmb><verAplic>RS1.0</verAplic><cOrgao>91</cOrgao>"
                    + "<cStat>136</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>"
                    + "<chNFe>" + CHAVE_ACESSO + "</chNFe><tpEvento>110140</tpEvento>"
                    + "<dhRegEvento>2026-03-15T10:00:00-03:00</dhRegEvento></infEvento></retEvento>"
                    + "</retEnvEvento>"
                    + "</nfeResultMsg></soap:Body></soap:Envelope>";

    @Test
    void deveRegistrarEpecComEventoAssinadoEInterpretarSucesso() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("envEvento").contains("tpEvento>110140").contains("descEvento>EPEC")
                    .contains("cOrgao>91").contains("Signature");
            return RESPOSTA_EPEC_REGISTRADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeEpecClient client = new NfeEpecClient(null, null, new AssinaturaXmlService());
            EpecResponse resposta = client.registrar(servidor.url(), nfe, CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO,
                    certificado, httpClient);

            assertThat(resposta.registrada()).isTrue();
            assertThat(resposta.codigoStatus()).isEqualTo("136");
        }
    }

    /** FIS-67: IE do emitente/destinatario e UF do destinatario iam direto no XML sem escape. */
    @Test
    void deveEscaparCaracteresEspeciaisNoEventoEpec() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NotaFiscalEletronica nfeBase = NotaFiscalEletronicaTestFixture.notaDeExemplo();

        String iePerigosa = "111222333</IE><Injetado>x</Injetado><IE>2";
        Emitente emitenteComIePerigosa = new Emitente(nfeBase.emitente().cnpj(), nfeBase.emitente().razaoSocial(),
                nfeBase.emitente().nomeFantasia(), iePerigosa, nfeBase.emitente().regimeTributario(),
                nfeBase.emitente().endereco());

        Endereco enderecoComUfPerigosa = new Endereco(nfeBase.destinatario().endereco().logradouro(),
                nfeBase.destinatario().endereco().numero(), nfeBase.destinatario().endereco().bairro(),
                nfeBase.destinatario().endereco().codigoMunicipio(), nfeBase.destinatario().endereco().municipio(),
                "SP</UF><Injetado>y</Injetado><UF>SP", nfeBase.destinatario().endereco().cep(),
                nfeBase.destinatario().endereco().telefone());
        Destinatario destinatarioComCamposPerigosos = new Destinatario(nfeBase.destinatario().cpfOuCnpj(),
                nfeBase.destinatario().razaoSocial(), nfeBase.destinatario().indicadorInscricaoEstadual(),
                "987</IE><Injetado>z</Injetado><IE>654", nfeBase.destinatario().email(), enderecoComUfPerigosa);

        NotaFiscalEletronica nfeAdulterada = new NotaFiscalEletronica(nfeBase.identificacao(), emitenteComIePerigosa,
                destinatarioComCamposPerigosos, nfeBase.itens(), nfeBase.pagamentos());

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).doesNotContain("<Injetado>")
                    .contains("&lt;Injetado&gt;x&lt;/Injetado&gt;")
                    .contains("&lt;Injetado&gt;y&lt;/Injetado&gt;")
                    .contains("&lt;Injetado&gt;z&lt;/Injetado&gt;");
            return RESPOSTA_EPEC_REGISTRADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeEpecClient client = new NfeEpecClient(null, null, new AssinaturaXmlService());
            client.registrar(servidor.url(), nfeAdulterada, CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO, certificado, httpClient);
        }
    }

    /** FIS-76: TDec_1302 exige 2 casas decimais fixas mesmo para zero (notas isentas/sem ICMS destacado). */
    @Test
    void deveFormatarValoresMonetariosZeroComDuasCasasDecimais() throws Exception {
        CertificadoCarregado certificado = certificadoDeTeste();
        NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaComImposto(
                NotaFiscalEletronicaTestFixture.impostoIcms40Isenta());

        try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
            assertThat(req).contains("<vICMS>0.00</vICMS>").contains("<vST>0.00</vST>").doesNotContain("<vICMS>0<");
            return RESPOSTA_EPEC_REGISTRADO;
        })) {
            HttpClient httpClient = new SefazHttpClientFactory()
                    .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

            NfeEpecClient client = new NfeEpecClient(null, null, new AssinaturaXmlService());
            client.registrar(servidor.url(), nfe, CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO, certificado, httpClient);
        }
    }

    /** FIS-84: dhEmi do evento EPEC tambem usava o fuso do SO em vez de um fuso fiscal fixo. */
    @Test
    void dhEmiDeveUsarFusoDoBrasilIndependenteDoFusoDoSistema() throws Exception {
        java.util.TimeZone fusoOriginal = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            CertificadoCarregado certificado = certificadoDeTeste();
            NotaFiscalEletronica nfe = NotaFiscalEletronicaTestFixture.notaDeExemplo();

            try (ServidorSoapDeTeste servidor = ServidorSoapDeTeste.iniciar(req -> {
                assertThat(req).contains("dhEmi>").doesNotContain("dhEmi>2026-03-15T00:00:00+00:00");
                assertThat(req).containsPattern("<dhEmi>2026-03-15T00:00:00-03:00");
                return RESPOSTA_EPEC_REGISTRADO;
            })) {
                HttpClient httpClient = new SefazHttpClientFactory()
                        .criarComTrustManager(certificado, servidor.trustManagerQueAceitaEsteServidor());

                NfeEpecClient client = new NfeEpecClient(null, null, new AssinaturaXmlService());
                client.registrar(servidor.url(), nfe, CHAVE_ACESSO, TipoAmbiente.HOMOLOGACAO, certificado, httpClient);
            }
        } finally {
            java.util.TimeZone.setDefault(fusoOriginal);
        }
    }

    private CertificadoCarregado certificadoDeTeste() throws Exception {
        char[] senha = "senha123".toCharArray();
        byte[] p12 = TestCertificadoFactory.gerarP12("12345678000199", senha,
                Date.from(Instant.now().minus(Duration.ofDays(1))), Date.from(Instant.now().plus(Duration.ofDays(365))));
        return new CertificadoDigitalService().carregar(TestCertificadoFactory.comoStream(p12), senha);
    }
}
