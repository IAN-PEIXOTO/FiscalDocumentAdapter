package com.fiscaladapter.certificado;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.ASN1TaggedObject;
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
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

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
     * Extrai o CNPJ do certificado conforme o padrao ICP-Brasil (OID 2.16.76.1.3.3). Um certificado
     * e-CNPJ REAL emitido por qualquer AC credenciada (confirmado empiricamente contra um
     * certificado real da RTI Sistemas) codifica esse OID dentro da extensao Subject Alternative
     * Name, como um "otherName" - NAO como um RDN do Subject DN. `TestCertificadoFactory` (usado em
     * todos os testes deste projeto) monta certificados sinteticos com o OID direto no Subject DN
     * por simplicidade, entao o codigo original (so verificava o Subject DN) nunca falhava nos
     * testes, apesar de nao funcionar contra NENHUM certificado real - so descoberto ao testar
     * contra um certificado de verdade.
     */
    private String extrairCnpj(X509Certificate certificado) {
        String cnpjDoSubjectDn = extrairCnpjDoSubjectDn(certificado);
        if (cnpjDoSubjectDn != null) {
            return cnpjDoSubjectDn;
        }
        return extrairCnpjDoSubjectAlternativeName(certificado);
    }

    /** Formato usado pelos certificados sinteticos de teste (ver TestCertificadoFactory). */
    private String extrairCnpjDoSubjectDn(X509Certificate certificado) {
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

    /** Formato usado por certificados e-CNPJ reais emitidos por qualquer AC da ICP-Brasil. */
    private String extrairCnpjDoSubjectAlternativeName(X509Certificate certificado) {
        try {
            Collection<List<?>> nomesAlternativos = certificado.getSubjectAlternativeNames();
            if (nomesAlternativos == null) {
                return null;
            }
            for (List<?> nome : nomesAlternativos) {
                if (!(nome.get(0) instanceof Integer tipo) || tipo != 0) {
                    continue; // 0 = otherName (ver GeneralName da RFC 5280)
                }
                if (!(nome.get(1) instanceof byte[] bytesDoOtherName)) {
                    continue;
                }
                String cnpj = extrairCnpjDoOtherName(bytesDoOtherName);
                if (cnpj != null) {
                    return cnpj;
                }
            }
            return null;
        } catch (CertificateParsingException e) {
            return null;
        }
    }

    private String extrairCnpjDoOtherName(byte[] bytesDoOtherName) {
        ASN1Sequence outroNome = ASN1Sequence.getInstance(bytesDoOtherName);
        ASN1ObjectIdentifier oid = ASN1ObjectIdentifier.getInstance(outroNome.getObjectAt(0));
        if (!OID_CNPJ_ICP_BRASIL.equals(oid)) {
            return null;
        }
        ASN1TaggedObject valorMarcado = ASN1TaggedObject.getInstance(outroNome.getObjectAt(1));
        ASN1Encodable valor = valorMarcado.getExplicitBaseObject();
        String texto = valor instanceof ASN1String asn1String ? asn1String.getString() : valor.toString();
        // a ICP-Brasil as vezes concatena outros dados apos o CNPJ nesse campo - os primeiros 14
        // digitos numericos sao sempre o CNPJ.
        String digitos = texto.replaceAll("\\D", "");
        return digitos.length() >= 14 ? digitos.substring(0, 14) : null;
    }

    public void validarNaoExpirado(CertificadoInfo info) {
        if (info.expirado(Instant.now())) {
            throw new CertificadoInvalidoException(
                    "Certificado do emissor (CNPJ " + info.cnpj() + ") expirou em " + info.validoAte());
        }
    }
}
