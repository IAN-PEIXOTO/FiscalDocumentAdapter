package com.fiscaladapter.seguranca;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Criptografa dados sensiveis antes de persistir em banco (LGPD/FIS-14) -
 * hoje usado para o corpo de resposta guardado pela idempotencia
 * (RequisicaoIdempotente.respostaJson), que contem o XML assinado da NFe
 * (CNPJ, enderecos, valores fiscais do emissor e destinatario).
 *
 * AES-256-GCM com IV aleatorio de 12 bytes por operacao, prefixado ao
 * ciphertext (formato: base64(iv || ciphertext || tag)). A chave vem de
 * fiscaladapter.seguranca.chave-criptografia (base64 de 32 bytes), que deve
 * ser fornecida via variavel de ambiente/secret manager em producao - nunca
 * commitada. Mesmo padrao de gestao de segredo documentado em
 * AuthorizationServerConfig para a chave RSA do Authorization Server.
 */
@Service
public class CriptografiaEmRepousoService {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKeySpec chave;
    private final SecureRandom secureRandom = new SecureRandom();

    public CriptografiaEmRepousoService(@Value("${fiscaladapter.seguranca.chave-criptografia}") String chaveBase64) {
        byte[] bytesChave = Base64.getDecoder().decode(chaveBase64);
        if (bytesChave.length != 32) {
            throw new IllegalStateException(
                    "fiscaladapter.seguranca.chave-criptografia precisa decodificar para 32 bytes (AES-256), tem " + bytesChave.length);
        }
        this.chave = new SecretKeySpec(bytesChave, "AES");
    }

    public String criptografar(String textoPuro) {
        return Base64.getEncoder().encodeToString(criptografarBytes(textoPuro.getBytes(StandardCharsets.UTF_8)));
    }

    public String descriptografar(String textoCriptografado) {
        return new String(descriptografarBytes(Base64.getDecoder().decode(textoCriptografado)), StandardCharsets.UTF_8);
    }

    /** Variante para dados binarios (ex.: arquivo .p12) - mesmo formato, sem passar por texto/UTF-8 no meio. */
    public byte[] criptografarBytes(byte[] dadoPuro) {
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(dadoPuro);

            byte[] saida = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, saida, 0, iv.length);
            System.arraycopy(ciphertext, 0, saida, iv.length, ciphertext.length);
            return saida;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar dado sensivel", e);
        }
    }

    public byte[] descriptografarBytes(byte[] dadoCriptografado) {
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            System.arraycopy(dadoCriptografado, 0, iv, 0, TAMANHO_IV_BYTES);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return cipher.doFinal(dadoCriptografado, TAMANHO_IV_BYTES, dadoCriptografado.length - TAMANHO_IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao descriptografar dado sensivel - chave incorreta ou dado corrompido", e);
        }
    }
}
