package com.fiscaladapter.certificado;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Enumeration;

/**
 * Carrega e valida certificados digitais A1 (PKCS#12) de emissores.
 * Nao persiste a chave privada em disco: o caller decide onde/como guardar o
 * arquivo .p12 (fora do repositorio, criptografado em repouso - ver FIS-14).
 */
@Service
public class CertificadoDigitalService {

    private static final ASN1ObjectIdentifier OID_CNPJ_ICP_BRASIL = new ASN1ObjectIdentifier("2.16.76.1.3.3");

    public CertificadoCarregado carregar(InputStream arquivoP12, char[] senha) {
        KeyStore keyStore = abrirKeyStore(arquivoP12, senha);
        String alias = primeiroAliasComChavePrivada(keyStore, senha);

        KeyStore.PrivateKeyEntry entry = obterEntrada(keyStore, alias, senha);
        X509Certificate certificado = (X509Certificate) entry.getCertificate();

        validarValidade(certificado);

        CertificadoInfo info = new CertificadoInfo(
                alias,
                certificado.getSubjectX500Principal().getName(),
                extrairCnpj(certificado),
                certificado.getNotBefore().toInstant(),
                certificado.getNotAfter().toInstant()
        );

        return new CertificadoCarregado(info, entry);
    }

    private KeyStore abrirKeyStore(InputStream arquivoP12, char[] senha) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(arquivoP12, senha);
            return keyStore;
        } catch (IOException e) {
            throw new CertificadoInvalidoException("Arquivo de certificado invalido ou senha incorreta", e);
        } catch (GeneralSecurityException e) {
            throw new CertificadoInvalidoException("Nao foi possivel processar o certificado PKCS#12", e);
        }
    }

    private String primeiroAliasComChavePrivada(KeyStore keyStore, char[] senha) {
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    return alias;
                }
            }
            throw new CertificadoInvalidoException("O arquivo PKCS#12 nao contem nenhuma chave privada");
        } catch (GeneralSecurityException e) {
            throw new CertificadoInvalidoException("Falha ao inspecionar o certificado", e);
        }
    }

    private KeyStore.PrivateKeyEntry obterEntrada(KeyStore keyStore, String alias, char[] senha) {
        try {
            KeyStore.Entry entry = keyStore.getEntry(alias, new KeyStore.PasswordProtection(senha));
            if (!(entry instanceof KeyStore.PrivateKeyEntry privateKeyEntry)) {
                throw new CertificadoInvalidoException("A entrada '" + alias + "' nao possui chave privada associada");
            }
            return privateKeyEntry;
        } catch (GeneralSecurityException e) {
            throw new CertificadoInvalidoException("Falha ao extrair a chave privada do certificado", e);
        }
    }

    private void validarValidade(X509Certificate certificado) {
        try {
            certificado.checkValidity();
        } catch (GeneralSecurityException e) {
            throw new CertificadoInvalidoException("Certificado expirado ou ainda nao valido", e);
        }
    }

    /**
     * Extrai o CNPJ do Subject DN conforme padrao ICP-Brasil (OID 2.16.76.1.3.3).
     * Retorna null se o certificado nao seguir o padrao (ex.: certificado de teste generico).
     */
    private String extrairCnpj(X509Certificate certificado) {
        try {
            X500Name subject = new JcaX509CertificateHolder(certificado).getSubject();
            RDN[] rdns = subject.getRDNs(OID_CNPJ_ICP_BRASIL);
            if (rdns.length == 0) {
                return null;
            }
            String valor = IETFUtils.valueToString(rdns[0].getFirst().getValue());
            return valor.replaceAll("\\D", "");
        } catch (CertificateEncodingException e) {
            return null;
        }
    }

    public void validarNaoExpirado(CertificadoInfo info) {
        if (info.expirado(Instant.now())) {
            throw new CertificadoInvalidoException(
                    "Certificado do emissor (CNPJ " + info.cnpj() + ") expirou em " + info.validoAte());
        }
    }
}
