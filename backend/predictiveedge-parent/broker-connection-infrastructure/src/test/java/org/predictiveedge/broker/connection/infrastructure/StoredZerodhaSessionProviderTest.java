package org.predictiveedge.broker.connection.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.connection.BrokerConnectionStore;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.zerodha.ZerodhaSession;

class StoredZerodhaSessionProviderTest {
    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");

    @Test
    void decryptsActiveMatchingAccountAndConditionallyEvictsRejectedToken() {
        var store = mock(BrokerConnectionStore.class); var cipher = mock(CredentialCipher.class);
        var userId = UUID.randomUUID(); var stored = stored("encrypted-v1");
        when(store.findZerodhaConnection(userId)).thenReturn(Optional.of(stored));
        when(cipher.decrypt("encrypted-v1")).thenReturn("token-v1");
        var provider = provider(store, cipher);
        var context = BrokerContext.withoutCredentials(userId, "ZD123");

        var session = provider.sessionFor(context);
        provider.authenticationFailed(context, session);

        assertThat(session.apiKey()).isEqualTo("api-key");
        assertThat(session.accessToken()).isEqualTo("token-v1");
        verify(store).deleteZerodhaConnection(userId, "encrypted-v1");
    }

    @Test
    void doesNotDeleteRotatedCredentialAfterOldSocketFails() {
        var store = mock(BrokerConnectionStore.class); var cipher = mock(CredentialCipher.class);
        var userId = UUID.randomUUID(); var stored = stored("encrypted-v2");
        when(store.findZerodhaConnection(userId)).thenReturn(Optional.of(stored));
        when(cipher.decrypt("encrypted-v2")).thenReturn("token-v2");

        provider(store, cipher).authenticationFailed(BrokerContext.withoutCredentials(userId, "ZD123"),
                new ZerodhaSession("api-key", "token-v1"));

        verify(store, never()).deleteZerodhaConnection(userId, "encrypted-v2");
    }

    @Test
    void failsClosedForExpiredOrMismatchedAccount() {
        var store = mock(BrokerConnectionStore.class); var cipher = mock(CredentialCipher.class);
        var userId = UUID.randomUUID();
        when(store.findZerodhaConnection(userId)).thenReturn(Optional.of(new BrokerConnectionStore.StoredBrokerConnection(
                "ZD123", "encrypted", "owner", Instant.parse("2026-08-04T01:00:00Z"),
                NOW.plusSeconds(60), null)));
        var provider = provider(store, cipher);

        assertThatThrownBy(() -> provider.sessionFor(BrokerContext.withoutCredentials(userId, "OTHER")))
                .isInstanceOf(BrokerFailure.class);
    }

    private static StoredZerodhaSessionProvider provider(BrokerConnectionStore store, CredentialCipher cipher) {
        return new StoredZerodhaSessionProvider(store, cipher, "api-key", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BrokerConnectionStore.StoredBrokerConnection stored(String encryptedToken) {
        return new BrokerConnectionStore.StoredBrokerConnection("ZD123", encryptedToken, "owner",
                Instant.parse("2026-08-05T01:00:00Z"), NOW.plusSeconds(60), null);
    }
}
