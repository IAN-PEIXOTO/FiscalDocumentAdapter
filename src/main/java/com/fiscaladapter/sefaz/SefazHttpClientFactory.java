package com.fiscaladapter.sefaz;

import com.fiscaladapter.certificado.CertificadoCarregado;
import org.springframework.stereotype.Component;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

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
