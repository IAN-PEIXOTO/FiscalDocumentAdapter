package com.fiscaladapter.documento.nfce;

import com.fiscaladapter.certificado.CertificadoCarregado;
import com.fiscaladapter.documento.nfe.Destinatario;
import com.fiscaladapter.documento.nfe.NotaFiscalEletronica;
import com.fiscaladapter.documento.nfe.TipoAmbiente;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Gera o conteudo do QR Code de consulta publica exigido no DANFE NFC-e
 * (FIS-17), versao 3 (NT 2025.001 v1.00, marco/2025 - a versao vigente; a
 * versao 2, baseada num CSC/token secreto por UF, fica documentada como nao
 * implementada aqui). Estrutura verificada contra o XSD oficial
 * (infNFeSupl/qrCode, padrao "QRCODE V3 ONLINE"/"QRCODE V3 OFFLINE" em
 * leiauteNFe_v4.00.xsd) e a implementacao de referencia nfephp-org/sped-nfe
 * (Factories\QRCode::get300).
 *
 * Emissao online (tpEmis 1/3/4, o caso comum): so chave+versao+ambiente, sem
 * nenhum segredo. Emissao offline/contingencia especifica do varejo (tpEmis
 * 9): a NFC-e e liberada sem contato com a SEFAZ - a "assinatura" do QR Code
 * e uma assinatura RSA/SHA-1 feita com a propria chave privada do emissor
 * (nao a assinatura XML-DSig do documento), permitindo ao leitor do QR Code
 * validar a autenticidade offline; a transmissao real da NFC-e para a SEFAZ
 * fica para quando a conexao for restabelecida (fila assincrona, fora do
 * escopo aqui - mesmo debito tecnico do FIS-30 para NFe/EPEC).
 */
@Component
public class NfceQrCodeService {

    private static final Pattern SEM_QUERY_STRING = Pattern.compile("\\?p=");

    public String gerarConteudoOnline(String chaveAcesso, TipoAmbiente ambiente, String urlConsulta) {
        return comParametroP(urlConsulta) + chaveAcesso + "|3|" + ambiente.codigo();
    }

    public String gerarConteudoOffline(NotaFiscalEletronica nfe, String chaveAcesso, String urlConsulta,
                                        CertificadoCarregado certificado) {
        TipoAmbiente ambiente = nfe.identificacao().ambiente();
        LocalDate dataEmissao = nfe.identificacao().dataEmissao();
        String dia = String.format("%02d", dataEmissao.getDayOfMonth());
        String valor = nfe.valorTotalNota().setScale(2, RoundingMode.HALF_UP).toPlainString();

        String tpIdDest = "";
        String cDest = "";
        Destinatario destinatario = nfe.destinatario();
        if (destinatario != null) {
            tpIdDest = destinatario.ehPessoaJuridica() ? "1" : "2";
            cDest = destinatario.documentoSemMascara();
        }

        String semAssinatura = chaveAcesso + "|3|" + ambiente.codigo() + "|" + dia + "|" + valor + "|" + tpIdDest + "|" + cDest;
        String assinatura = assinar(semAssinatura, certificado);

        return comParametroP(urlConsulta) + semAssinatura + "|" + assinatura;
    }

    /** Insere &lt;infNFeSupl&gt; (qrCode + urlChave) no XML JA ASSINADO, como irmao de infNFe antes de Signature -
     * nao invalida a assinatura porque a Reference da assinatura cobre so a subarvore de infNFe, nao os irmaos. */
    public String inserirInfNFeSupl(String xmlAssinado, String conteudoQrCode, String urlConsultaPorChave) {
        String bloco = "<infNFeSupl>"
                + "<qrCode>" + escaparXml(conteudoQrCode) + "</qrCode>"
                + "<urlChave>" + escaparXml(urlConsultaPorChave) + "</urlChave>"
                + "</infNFeSupl>";

        int posicaoSignature = xmlAssinado.indexOf("<Signature");
        if (posicaoSignature < 0) {
            throw new IllegalArgumentException("XML nao contem <Signature> - infNFeSupl so pode ser inserido apos a assinatura");
        }
        return xmlAssinado.substring(0, posicaoSignature) + bloco + xmlAssinado.substring(posicaoSignature);
    }

    private String comParametroP(String urlConsulta) {
        if (SEM_QUERY_STRING.matcher(urlConsulta).find()) {
            return urlConsulta;
        }
        return urlConsulta + "?p=";
    }

    private String assinar(String conteudo, CertificadoCarregado certificado) {
        try {
            PrivateKey chavePrivada = certificado.chaveEEntidade().getPrivateKey();
            Signature assinatura = Signature.getInstance("SHA1withRSA");
            assinatura.initSign(chavePrivada);
            assinatura.update(conteudo.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(assinatura.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o QR Code offline da NFC-e", e);
        }
    }

    private String escaparXml(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
