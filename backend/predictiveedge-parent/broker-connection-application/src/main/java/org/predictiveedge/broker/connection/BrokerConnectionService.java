package org.predictiveedge.broker.connection;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.predictiveedge.broker.zerodha.ZerodhaLoginClient;
import org.predictiveedge.broker.zerodha.ZerodhaLoginFlow;
import org.predictiveedge.broker.zerodha.ZerodhaSessionClient;

public final class BrokerConnectionService {
    private static final ZoneId KITE_ZONE = ZoneId.of("Asia/Kolkata");
    private static final int REVOCATION_BATCH_SIZE = 100;
    private final BrokerConnectionStore store;
    private final CredentialCipher credentials;
    private final ZerodhaLoginClient loginClient;
    private final ZerodhaSessionClient sessionClient;
    private final Settings settings;
    private final Clock clock;
    private final SecureRandom random;

    public BrokerConnectionService(
            BrokerConnectionStore store,
            CredentialCipher credentials,
            ZerodhaLoginClient loginClient,
            ZerodhaSessionClient sessionClient,
            Settings settings,
            Clock clock,
            SecureRandom random) {
        this.store = store;
        this.credentials = credentials;
        this.loginClient = loginClient;
        this.sessionClient = sessionClient;
        this.settings = settings;
        this.clock = clock;
        this.random = random;
    }

    public ConnectionOverview overview(UUID userId, String predictiveEdgeSession) {
        String ownerSessionHash = hashRequiredSession(predictiveEdgeSession);
        Optional<BrokerConnectionStore.StoredBrokerConnection> connection = activeConnection(
                userId, ownerSessionHash, true);
        return new ConnectionOverview(
                settings.configured(), connection.isPresent(),
                connection.map(BrokerConnectionStore.StoredBrokerConnection::externalAccountId).orElse(null),
                connection.map(BrokerConnectionStore.StoredBrokerConnection::connectedAt).orElse(null),
                connection.map(value -> tokenExpiresAt(value.connectedAt())).orElse(null),
                connection.map(BrokerConnectionStore.StoredBrokerConnection::leaseExpiresAt).orElse(null),
                true, true, false);
    }

    public URI beginZerodhaConnection(UUID userId, String predictiveEdgeSession) {
        requireConfigured();
        String ownerSessionHash = hashRequiredSession(predictiveEdgeSession);
        if (activeConnection(userId, ownerSessionHash, true).isPresent()) {
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.ALREADY_CONNECTED,
                    "Zerodha is already connected; disconnect it before starting a new session");
        }
        if (store.findZerodhaConnection(userId).isPresent()) {
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.ALREADY_CONNECTED,
                    "Zerodha session revocation is still in progress; retry shortly");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.createState(hash(state), userId, ownerSessionHash, clock.instant().plus(settings.stateTtl()));
        return ZerodhaLoginFlow.loginUri(settings.apiKey(), "state=" + state);
    }

    public void signalBrowserClosing(UUID userId, String predictiveEdgeSession) {
        String ownerSessionHash = hashRequiredSession(predictiveEdgeSession);
        Instant now = clock.instant();
        store.shortenZerodhaLease(userId, ownerSessionHash, now, now.plus(settings.closeGrace()));
    }

    public void disconnectZerodha(UUID userId) {
        Optional<BrokerConnectionStore.ClaimedBrokerConnection> connection =
                store.claimZerodhaConnectionForRevocation(userId, clock.instant());
        connection.ifPresent(this::revokeClaimedConnectionOrThrow);
    }

    public void revokeExpiredLeases() {
        for (var connection : store.claimExpiredZerodhaConnections(clock.instant(), REVOCATION_BATCH_SIZE)) {
            try {
                revokeClaimedConnection(connection);
            } catch (RuntimeException failure) {
                store.releaseZerodhaRevocation(connection, clock.instant());
            }
        }
    }

    public URI completeZerodhaConnection(String state, String requestToken) {
        requireConfigured();
        if (state == null || state.isBlank() || requestToken == null || requestToken.isBlank()) {
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.INVALID_STATE,
                    "Zerodha callback is missing its state or request token");
        }
        BrokerConnectionStore.PendingConnection pending = store.consumeState(hash(state), clock.instant())
                .orElseThrow(() -> new BrokerConnectionFailure(BrokerConnectionFailure.Code.INVALID_STATE,
                        "Zerodha connection state is invalid or expired"));
        try {
            var grant = loginClient.exchangeRequestToken(settings.apiKey(), requestToken, settings.apiSecret());
            Instant connectedAt = clock.instant();
            store.saveZerodhaConnection(pending.userId(), grant.userId(), credentials.encrypt(grant.accessToken()),
                    pending.ownerSessionHash(), connectedAt, connectedAt.plus(settings.leaseTtl()));
            return URI.create(settings.webBaseUrl() + "?broker=zerodha&connected=true");
        } catch (RuntimeException failure) {
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.CONNECTION_FAILED,
                    "Zerodha connection could not be completed");
        }
    }

    private Optional<BrokerConnectionStore.StoredBrokerConnection> activeConnection(
            UUID userId, String ownerSessionHash, boolean renewLease) {
        Optional<BrokerConnectionStore.StoredBrokerConnection> connection = store.findZerodhaConnection(userId);
        if (connection.isEmpty()) {
            return connection;
        }
        var current = connection.get();
        Instant now = clock.instant();
        if (!now.isBefore(tokenExpiresAt(current.connectedAt()))) {
            store.deleteZerodhaConnection(userId);
            return Optional.empty();
        }
        if (current.revocationStartedAt() != null
                || !current.ownerSessionHash().equals(ownerSessionHash)
                || !now.isBefore(current.leaseExpiresAt())) {
            disconnectZerodha(userId);
            return Optional.empty();
        }
        if (renewLease) {
            Instant renewedUntil = now.plus(settings.leaseTtl());
            if (!store.renewZerodhaLease(userId, ownerSessionHash, now, renewedUntil)) {
                return Optional.empty();
            }
            current = new BrokerConnectionStore.StoredBrokerConnection(current.externalAccountId(),
                    current.encryptedAccessToken(), current.ownerSessionHash(), current.connectedAt(),
                    renewedUntil, current.revocationStartedAt());
        }
        return Optional.of(current);
    }

    private void revokeClaimedConnectionOrThrow(BrokerConnectionStore.ClaimedBrokerConnection connection) {
        try {
            revokeClaimedConnection(connection);
        } catch (RuntimeException failure) {
            store.releaseZerodhaRevocation(connection, clock.instant());
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.CONNECTION_FAILED,
                    "Zerodha session could not be disconnected; automatic revocation will retry");
        }
    }

    private void revokeClaimedConnection(BrokerConnectionStore.ClaimedBrokerConnection connection) {
        String accessToken = credentials.decrypt(connection.encryptedAccessToken());
        sessionClient.invalidate(settings.apiKey(), accessToken);
        store.completeZerodhaRevocation(connection);
    }

    private void requireConfigured() {
        if (!settings.configured()) {
            throw new BrokerConnectionFailure(BrokerConnectionFailure.Code.NOT_CONFIGURED,
                    "Configure PE_ZERODHA_API_KEY and PE_ZERODHA_API_SECRET first");
        }
    }

    private static Instant tokenExpiresAt(Instant connectedAt) {
        return connectedAt.atZone(KITE_ZONE).toLocalDate().plusDays(1)
                .atTime(LocalTime.of(6, 0)).atZone(KITE_ZONE).toInstant();
    }

    private static String hashRequiredSession(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("PredictiveEdge session is required");
        }
        return hash(value);
    }

    private static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Settings(String apiKey, String apiSecret, String webBaseUrl, Duration stateTtl,
            Duration leaseTtl, Duration closeGrace) {
        public boolean configured() {
            return apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
        }
    }

    public record ConnectionOverview(
            boolean zerodhaConfigured,
            boolean zerodhaConnected,
            String zerodhaAccountId,
            Instant zerodhaConnectedAt,
            Instant zerodhaSessionExpiresAt,
            Instant browserLeaseExpiresAt,
            boolean paperTradingAvailable,
            boolean backtestingAvailable,
            boolean liveTradingEnabled) {}
}
