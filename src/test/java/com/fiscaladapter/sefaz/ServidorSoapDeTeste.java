package com.fiscaladapter.sefaz;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

/**
 * Servidor HTTPS local com mTLS (confia em qualquer certificado de cliente,
 * ja que aqui o objetivo e testar a mecanica de handshake+SOAP do nosso
 * cliente, nao validacao de certificado real da ICP-Brasil). Usado para
 * verificar de ponta a ponta o fluxo de comunicacao com a SEFAZ sem
 * depender do ambiente de homologacao real, que nao e acessivel nesta sessao.
 */
public final class ServidorSoapDeTeste implements AutoCloseable {

    private final HttpsServer servidor;

    private ServidorSoapDeTeste(HttpsServer servidor) {
        this.servidor = servidor;
    }

    public static ServidorSoapDeTeste iniciar(Function<String, String> respostaParaCadaRequisicao) throws Exception {
        KeyStore keyStore = gerarKeyStoreComCertificadoLocalhost();

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "senhaServidor".toCharArray());

        TrustManager confiaEmQualquerCliente = new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), new TrustManager[]{confiaEmQualquerCliente}, new SecureRandom());

        HttpsServer servidor = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = sslContext.getDefaultSSLParameters();
                sslParameters.setNeedClientAuth(true);
                params.setSSLParameters(sslParameters);
            }
        });

        servidor.createContext("/", exchange -> {
            String corpoRequisicao = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String respostaXml = respostaParaCadaRequisicao.apply(corpoRequisicao);
            byte[] bytesResposta = respostaXml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/soap+xml; charset=utf-8");
            exchange.sendResponseHeaders(200, bytesResposta.length);
            exchange.getResponseBody().write(bytesResposta);
            exchange.close();
        });

        // sem isso, com.sun.net.httpserver serializa todas as requisicoes numa unica thread
        // (util para os testes de fluxo unico existentes, mas corromperia qualquer teste de
        // concorrencia real - ver NfeEmissaoCargaTest, FIS-41 - fazendo parecer que o gargalo
        // e o servidor SEFAZ simulado, quando na verdade seria so essa serializacao artificial).
        servidor.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        servidor.start();
        return new ServidorSoapDeTeste(servidor);
    }

    public int porta() {
        return servidor.getAddress().getPort();
    }

    public String url() {
        return "https://localhost:" + porta() + "/";
    }

    public TrustManager trustManagerQueAceitaEsteServidor() {
        return new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    @Override
    public void close() {
        servidor.stop(0);
    }

    private static KeyStore gerarKeyStoreComCertificadoLocalhost() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name subject = new X500Name("CN=localhost");
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(System.identityHashCode(keyPair)),
                Date.from(Instant.now().minus(Duration.ofDays(1))),
                Date.from(Instant.now().plus(Duration.ofDays(1))),
                subject,
                keyPair.getPublic()
        );
        certBuilder.addExtension(Extension.subjectAlternativeName, false,
                new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("servidor-teste", keyPair.getPrivate(), "senhaServidor".toCharArray(),
                new X509Certificate[]{certificate});
        return keyStore;
    }
}
