package org.predictiveedge.broker.connection.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;

class AesGcmCredentialCipherTest {
    @Test
    void encryptsWithAuthenticatedRandomizedCiphertext() {
        var cipher = new AesGcmCredentialCipher("a-local-test-secret-with-more-than-32-characters", new SecureRandom());

        String first = cipher.encrypt("daily-access-token");
        String second = cipher.encrypt("daily-access-token");

        assertThat(first).isNotEqualTo(second).doesNotContain("daily-access-token");
        assertThat(cipher.decrypt(first)).isEqualTo("daily-access-token");
        assertThat(cipher.decrypt(second)).isEqualTo("daily-access-token");
    }
}
