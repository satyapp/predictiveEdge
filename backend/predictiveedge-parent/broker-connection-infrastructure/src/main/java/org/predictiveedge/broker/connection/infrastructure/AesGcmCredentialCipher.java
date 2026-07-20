package org.predictiveedge.broker.connection.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.predictiveedge.broker.connection.CredentialCipher;

public final class AesGcmCredentialCipher implements CredentialCipher {
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random;

    public AesGcmCredentialCipher(String masterSecret, SecureRandom random) {
        if (masterSecret == null || masterSecret.length() < 32) {
            throw new IllegalArgumentException("Broker credential key must contain at least 32 characters");
        }
        try {
            this.key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest(masterSecret.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
        this.random = random;
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (Exception failure) {
            throw new IllegalStateException("Credential encryption failed", failure);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(ciphertext));
            byte[] nonce = new byte[NONCE_BYTES];
            payload.get(nonce);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw new IllegalStateException("Credential decryption failed", failure);
        }
    }
}
