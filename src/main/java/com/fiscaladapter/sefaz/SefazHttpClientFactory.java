package com.fiscaladapter.sefaz;

import com.fiscaladapter.certificado.CertificadoCarregado;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Monta um HttpClient com autenticacao mTLS usando o certificado do emissor
 * (a SEFAZ exige que o cliente TLS apresente o certificado A1 do
 * contribuinte, nao apenas usuario/senha).
 *
 * O trust manager usado para validar o certificado do SERVIDOR (a SEFAZ) combina o cacerts padrao
 * da JVM com as raizes da ICP-Brasil (ver {@link #ICP_BRASIL_ROOT_RESOURCES}) - confirmado por
 * teste real contra a SEFAZ-PR de homologacao que o cacerts padrao do JDK sozinho NAO confia na
 * cadeia (webservices de SEFAZ/orgaos de governo brasileiros usam certificados emitidos sob a
 * ICP-Brasil, uma cadeia nacional que nao faz parte do conjunto de CAs comerciais/internacionais
 * que vem por padrao no JDK) - sem isso, toda chamada a qualquer SEFAZ falharia com
 * SunCertPathBuilderException, mesmo com o certificado do CLIENTE correto.
 */
@Component
public class SefazHttpClientFactory {

    /**
     * Raizes atualmente validas da ICP-Brasil (v1 expirou em 2021, v3/v8/v9 foram revogadas, v11 e
     * exclusiva para assinatura de codigo - nao se aplica a TLS), baixadas da fonte oficial do ITI
     * (ver src/main/resources/certificados/icp-brasil/README.md para a procedencia e como
     * atualizar). v4 e v13 ficaram de fora: usam chave EC com parametros explicitos (curva nao
     * nomeada por OID), formato que o provider X.509 padrao do JDK rejeita
     * ("java.io.IOException: Only named ECParameters supported") - nenhuma das duas e a raiz usada
     * por certificados de SSL/TLS de servidor (essa e a v10, RSA, confirmada contra a SEFAZ-PR).
     */
    private static final List<String> ICP_BRASIL_ROOT_RESOURCES = List.of(
            "certificados/icp-brasil/acraiz-v2.crt",
            "certificados/icp-brasil/acraiz-v5.crt",
            "certificados/icp-brasil/acraiz-v6.crt",
            "certificados/icp-brasil/acraiz-v7.crt",
            "certificados/icp-brasil/acraiz-v10.crt",
            "certificados/icp-brasil/acraiz-v12.crt"
    );

    /**
     * Montado uma unica vez (o trust store e o mesmo para qualquer certificado de cliente/emissor
     * - nao depende de qual contribuinte esta chamando).
     */
    private static final TrustManager[] TRUST_MANAGERS_COM_ICP_BRASIL = construirTrustManagers();

    private static TrustManager[] construirTrustManagers() {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            // comeca com as autoridades certificadoras padrao da JVM (cacerts) - preserva a
            // confianca em CAs comerciais/internacionais para qualquer endpoint que nao use
            // ICP-Brasil.
            TrustManagerFactory tmfPadrao = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmfPadrao.init((KeyStore) null);
            for (TrustManager tm : tmfPadrao.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509) {
                    X509Certificate[] emissoresPadrao = x509.getAcceptedIssuers();
                    for (int i = 0; i < emissoresPadrao.length; i++) {
                        trustStore.setCertificateEntry("cacerts-" + i, emissoresPadrao[i]);
                    }
                }
            }

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            for (String recurso : ICP_BRASIL_ROOT_RESOURCES) {
                try (InputStream entrada = SefazHttpClientFactory.class.getClassLoader().getResourceAsStream(recurso)) {
                    if (entrada == null) {
                        throw new IllegalStateException("Recurso de certificado raiz da ICP-Brasil nao encontrado: " + recurso);
                    }
                    X509Certificate certificadoRaiz = (X509Certificate) certificateFactory.generateCertificate(entrada);
                    trustStore.setCertificateEntry(recurso, certificadoRaiz);
                }
            }

            TrustManagerFactory tmfCombinado = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmfCombinado.init(trustStore);
            return tmfCombinado.getTrustManagers();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao montar o trust store com as raizes da ICP-Brasil", e);
        }
    }

    /**
     * Cache por instancia de certificado carregado (FIS-78): monta o HttpClient/SSLContext uma
     * unica vez e reaproveita entre as varias chamadas SOAP que uma mesma operacao faz com o
     * MESMO CertificadoCarregado (ex.: tentativa normal + contingencia + EPEC + consulta de prazo
     * de cancelamento, todas na mesma emissao/consulta) - evita repetir o parsing do keystore
     * PKCS12 e a inicializacao do SSLContext a cada chamada individual.
     *
     * Chave fraca (WeakHashMap, com acesso sincronizado pois WeakHashMap nao e thread-safe): o
     * certificado e recarregado a cada chamada a CertificadoEmissorService.carregar, entao nao ha
     * um unico CertificadoCarregado de vida longa para cachear por CNPJ - em vez disso, a entrada
     * e valida so enquanto o proprio CertificadoCarregado (com a chave privada em memoria) ainda
     * estiver referenciado em algum lugar (ex.: durante o processamento da requisicao atual); ao
     * ser coletado pelo GC, a entrada correspondente (e o SSLContext associado) tambem some, sem
     * exigir um TTL/limite de tamanho explicito nem prolongar a vida do material de chave privada.
     */
    private final Map<CertificadoCarregado, HttpClient> cachePorCertificado =
            Collections.synchronizedMap(new WeakHashMap<>());

    public HttpClient criar(CertificadoCarregado certificado) {
        return cachePorCertificado.computeIfAbsent(certificado, this::montar);
    }

    private HttpClient montar(CertificadoCarregado certificado) {
        try {
            char[] senhaEfemera = "senha-em-memoria".toCharArray();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setEntry("emissor", certificado.chaveEEntidade(), new KeyStore.PasswordProtection(senhaEfemera));

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, senhaEfemera);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), TRUST_MANAGERS_COM_ICP_BRASIL, new SecureRandom());

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
