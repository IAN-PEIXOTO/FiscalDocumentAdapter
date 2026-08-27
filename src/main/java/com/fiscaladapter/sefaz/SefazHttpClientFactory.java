package com.fiscaladapter.sefaz;

import com.fiscaladapter.certificado.CertificadoCarregado;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * Monta um HttpClient com autenticacao mTLS usando o certificado do emissor
 * (a SEFAZ exige que o cliente TLS apresente o certificado A1 do
 * contribuinte, nao apenas usuario/senha).
 *
 * ATENCAO (nao verificavel nesta sessao, sem acesso a homologacao real): o
 * trust manager usado aqui e o padrao da JVM (cacerts). A cadeia de CA raiz
 * da ICP-Brasil pode nao estar presente no cacerts padrao do JDK, o que
 * causaria falha de handshake TLS ao tentar validar o certificado do
 * servidor da SEFAZ. Antes do primeiro teste real contra homologacao,
 * validar se e necessario importar a cadeia da ICP-Brasil num truststore
 * proprio.
 */
@Component
public class SefazHttpClientFactory {

    public HttpClient criar(CertificadoCarregado certificado) {
        try {
            char[] senhaEfemera = "senha-em-memoria".toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setEntry("emissor", certificado.chaveEEntidade(), new KeyStore.PasswordProtection(senhaEfemera));

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, senhaEfemera);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
        } catch (Exception e) {
            throw new SefazComunicacaoException("Falha ao montar cliente HTTP com mTLS", e);
        }
    }

    /** Variante para testes locais, permitindo customizar tambem o trust manager (ex.: confiar num cert de teste). */
    public HttpClient criarComTrustManager(CertificadoCarregado certificado, javax.net.ssl.TrustManager trustManager) {
        try {
            char[] senhaEfemera = "senha-em-memoria".toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setEntry("emissor", certificado.chaveEEntidade(), new KeyStore.PasswordProtection(senhaEfemera));

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, senhaEfemera);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), new javax.net.ssl.TrustManager[]{trustManager}, new SecureRandom());

            return HttpClient.newBuilder().sslContext(sslContext).connectTimeout(Duration.ofSeconds(30)).build();
        } catch (Exception e) {
            throw new SefazComunicacaoException("Falha ao montar cliente HTTP com mTLS", e);
        }
    }
}
