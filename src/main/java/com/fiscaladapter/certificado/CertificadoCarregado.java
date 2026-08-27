package com.fiscaladapter.certificado;

import java.security.KeyStore;

/**
 * Certificado A1 carregado e validado, pronto para uso na assinatura digital (FIS-4).
 * A chave privada so existe em memoria durante o processamento da requisicao.
 */
public record CertificadoCarregado(CertificadoInfo info, KeyStore.PrivateKeyEntry chaveEEntidade) {
}
