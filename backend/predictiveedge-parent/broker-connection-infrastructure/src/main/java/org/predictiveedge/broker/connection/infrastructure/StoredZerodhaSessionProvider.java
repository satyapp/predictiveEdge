package org.predictiveedge.broker.connection.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import org.predictiveedge.broker.connection.BrokerConnectionStore;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.zerodha.ZerodhaSession;
import org.predictiveedge.broker.zerodha.ZerodhaSessionProvider;

/** Reads encrypted user-scoped Zerodha sessions and conditionally evicts rejected credentials. */
public final class StoredZerodhaSessionProvider implements ZerodhaSessionProvider {
    private static final ZoneId KITE_ZONE = ZoneId.of("Asia/Kolkata");
    private final BrokerConnectionStore store;
    private final CredentialCipher credentials;
    private final String apiKey;
    private final Clock clock;

    public StoredZerodhaSessionProvider(BrokerConnectionStore store, CredentialCipher credentials,
            String apiKey, Clock clock) {
        this.store = Objects.requireNonNull(store); this.credentials = Objects.requireNonNull(credentials);
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("Zerodha API key is required");
        this.apiKey = apiKey; this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ZerodhaSession sessionFor(BrokerContext context) {
        Objects.requireNonNull(context);
        var stored = store.findZerodhaConnection(context.userId()).orElseThrow(() -> unavailable("not connected"));
        if (stored.revocationStartedAt() != null || !clock.instant().isBefore(expiresAt(stored.connectedAt())))
            throw unavailable("expired or being revoked");
        if (!stored.externalAccountId().equals(context.brokerAccountId()))
            throw unavailable("account identity does not match");
        return new ZerodhaSession(apiKey, credentials.decrypt(stored.encryptedAccessToken()));
    }

    @Override
    public void authenticationFailed(BrokerContext context, ZerodhaSession rejectedSession) {
        Objects.requireNonNull(context); Objects.requireNonNull(rejectedSession);
        store.findZerodhaConnection(context.userId()).ifPresent(stored -> {
            String currentToken = credentials.decrypt(stored.encryptedAccessToken());
            if (MessageDigest.isEqual(currentToken.getBytes(StandardCharsets.UTF_8),
                    rejectedSession.accessToken().getBytes(StandardCharsets.UTF_8)))
                store.deleteZerodhaConnection(context.userId(), stored.encryptedAccessToken());
        });
    }

    private java.time.Instant expiresAt(java.time.Instant connectedAt) {
        return connectedAt.atZone(KITE_ZONE).toLocalDate().plusDays(1).atTime(LocalTime.of(6, 0))
                .atZone(KITE_ZONE).toInstant();
    }

    private static BrokerFailure unavailable(String reason) {
        return new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                "Zerodha session is " + reason);
    }
}
