package com.fiscaladapter.certificado;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Gera certificados PKCS#12 auto-assinados em memoria, apenas para teste.
 * Nunca usar em producao - nao tem cadeia de confianca da ICP-Brasil.
 */
public final class TestCertificadoFactory {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private TestCertificadoFactory() {
    }

    public static byte[] gerarP12(String cnpj, char[] senha, Date validoDe, Date validoAte) throws Exception {
        String subjectDn = "CN=EMPRESA TESTE LTDA:" + cnpj + ", OID.2.16.76.1.3.3=" + cnpj + ", O=ICP-Brasil, C=BR";
        return gerarP12ComSubjectDn(subjectDn, senha, validoDe, validoAte);
    }

    /** FIS-85: certificado sem o RDN do OID ICP-Brasil (2.16.76.1.3.3) - simula um certificado generico/de teste. */
    public static byte[] gerarP12SemOidIcpBrasil(char[] senha, Date validoDe, Date validoAte) throws Exception {
        String subjectDn = "CN=CERTIFICADO GENERICO SEM PADRAO ICP-BRASIL, O=Alguma CA, C=BR";
        return gerarP12ComSubjectDn(subjectDn, senha, validoDe, validoAte);
    }

    private static byte[] gerarP12ComSubjectDn(String subjectDn, char[] senha, Date validoDe, Date validoAte) throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name(subjectDn);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.identityHashCode(keyPair)),
                validoDe,
                validoAte,
                subject,
                keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("emissor-teste", keyPair.getPrivate(), senha, new X509Certificate[]{certificate});

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyStore.store(out, senha);
        return out.toByteArray();
    }

    public static ByteArrayInputStream comoStream(byte[] p12) {
        return new ByteArrayInputStream(p12);
    }
}
