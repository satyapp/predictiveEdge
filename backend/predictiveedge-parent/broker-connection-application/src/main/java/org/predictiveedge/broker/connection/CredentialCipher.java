package org.predictiveedge.broker.connection;

public interface CredentialCipher {
    String encrypt(String plaintext);
    String decrypt(String ciphertext);
}
