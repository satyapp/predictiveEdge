package org.predictiveedge.broker.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.zerodha.ZerodhaLoginClient;
import org.predictiveedge.broker.zerodha.ZerodhaSessionClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class BrokerConnectionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-16T12:00:00Z");
    private static final String SESSION = "Bearer predictiveedge-session";

    @Test
    void bindsTheOneTimeAuthorizationStateToTheCurrentPredictiveEdgeSession() {
        RecordingStore store = new RecordingStore();
        var service = service(store, (uri, headers) -> 200, NOW);
        UUID userId = UUID.randomUUID();

        var loginUri = service.beginZerodhaConnection(userId, SESSION);

        assertThat(loginUri.toString()).startsWith("https://kite.zerodha.com/connect/login?v=3&api_key=key")
                .contains("redirect_params=state%3D");
        assertThat(store.userId).isEqualTo(userId);
        assertThat(store.ownerSessionHash).isEqualTo(hash(SESSION));
        assertThat(store.expiresAt).isEqualTo("2026-07-16T12:10:00Z");
    }

    @Test
    void invalidatesTheRemoteSessionBeforeDeletingTheLocalConnection() {
        RecordingStore store = connectedStore(SESSION, NOW.plus(Duration.ofMinutes(2)));
        var service = service(store, (uri, headers) -> {
            assertThat(store.deleted).isFalse();
            assertThat(uri.toString()).contains("access_token=encrypted-token");
            return 200;
        }, NOW);

        service.disconnectZerodha(store.userId);

        assertThat(store.deleted).isTrue();
    }

    @Test
    void aNewPredictiveEdgeLoginCannotInheritTheOldBrokerSession() {
        RecordingStore store = connectedStore("Bearer old-session", NOW.plus(Duration.ofMinutes(2)));
        var service = service(store, (uri, headers) -> 200, NOW);

        var overview = service.overview(store.userId, "Bearer new-session");

        assertThat(overview.zerodhaConnected()).isFalse();
        assertThat(store.deleted).isTrue();
    }

    @Test
    void anActiveBrowserHeartbeatRenewsTheBrokerLease() {
        RecordingStore store = connectedStore(SESSION, NOW.plusSeconds(40));
        var service = service(store, (uri, headers) -> 200, NOW);

        var overview = service.overview(store.userId, SESSION);

        assertThat(overview.zerodhaConnected()).isTrue();
        assertThat(store.renewedUntil).isEqualTo(NOW.plusSeconds(120));
    }

    @Test
    void theLeaseReaperInvalidatesExpiredBrowserSessions() {
        RecordingStore store = new RecordingStore();
        UUID userId = UUID.randomUUID();
        store.expired.add(new BrokerConnectionStore.ClaimedBrokerConnection(userId, "encrypted-token"));
        var service = service(store, (uri, headers) -> 200, NOW);

        service.revokeExpiredLeases();

        assertThat(store.completedRevocations).containsExactly(userId);
    }

    @Test
    void removesAConnectionAfterTheKiteDailySessionExpiry() {
        RecordingStore store = connectedStore(SESSION, Instant.parse("2026-07-17T01:00:00Z"));
        store.connection = Optional.of(new BrokerConnectionStore.StoredBrokerConnection(
                "ZD1234", "encrypted-token", hash(SESSION), Instant.parse("2026-07-16T12:00:00Z"),
                Instant.parse("2026-07-17T01:00:00Z"), null));
        var service = service(store, (uri, headers) -> 200, Instant.parse("2026-07-17T00:30:00Z"));

        var overview = service.overview(store.userId, SESSION);

        assertThat(overview.zerodhaConnected()).isFalse();
        assertThat(store.deleted).isTrue();
    }

    private static BrokerConnectionService service(RecordingStore store,
            org.predictiveedge.broker.zerodha.ZerodhaSessionTerminationTransport termination,
            Instant now) {
        return new BrokerConnectionService(
                store, new PassthroughCipher(),
                new ZerodhaLoginClient((uri, headers, form) -> "{}", new ObjectMapper()),
                new ZerodhaSessionClient(termination),
                new BrokerConnectionService.Settings("key", "secret", "http://localhost:3000/",
                        Duration.ofMinutes(10), Duration.ofSeconds(120), Duration.ofSeconds(30)),
                Clock.fixed(now, ZoneOffset.UTC), new java.security.SecureRandom());
    }

    private static RecordingStore connectedStore(String session, Instant leaseExpiresAt) {
        RecordingStore store = new RecordingStore();
        store.userId = UUID.randomUUID();
        store.connection = Optional.of(new BrokerConnectionStore.StoredBrokerConnection(
                "ZD1234", "encrypted-token", hash(session), NOW, leaseExpiresAt, null));
        return store;
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class RecordingStore implements BrokerConnectionStore {
        String stateHash;
        UUID userId;
        String ownerSessionHash;
        Instant expiresAt;
        Instant renewedUntil;
        boolean deleted;
        Optional<StoredBrokerConnection> connection = Optional.empty();
        List<ClaimedBrokerConnection> expired = new ArrayList<>();
        List<UUID> completedRevocations = new ArrayList<>();

        public void createState(String hash, UUID user, String sessionHash, Instant expiry) {
            stateHash = hash; userId = user; ownerSessionHash = sessionHash; expiresAt = expiry;
        }
        public Optional<PendingConnection> consumeState(String stateHash, Instant now) { return Optional.empty(); }
        public void saveZerodhaConnection(UUID userId, String externalAccountId, String token,
                String sessionHash, Instant connectedAt, Instant leaseExpiresAt) {}
        public Optional<StoredBrokerConnection> findZerodhaConnection(UUID userId) { return connection; }
        public boolean renewZerodhaLease(UUID userId, String sessionHash, Instant now, Instant leaseExpiresAt) {
            renewedUntil = leaseExpiresAt; return connection.isPresent();
        }
        public void shortenZerodhaLease(UUID userId, String sessionHash, Instant now, Instant leaseExpiresAt) {
            renewedUntil = leaseExpiresAt;
        }
        public Optional<ClaimedBrokerConnection> claimZerodhaConnectionForRevocation(UUID userId, Instant now) {
            return connection.map(value -> new ClaimedBrokerConnection(userId, value.encryptedAccessToken()));
        }
        public List<ClaimedBrokerConnection> claimExpiredZerodhaConnections(Instant now, int limit) {
            return List.copyOf(expired);
        }
        public void completeZerodhaRevocation(ClaimedBrokerConnection claimed) {
            completedRevocations.add(claimed.userId()); deleted = true; connection = Optional.empty();
        }
        public void releaseZerodhaRevocation(ClaimedBrokerConnection claimed, Instant now) {}
        public void deleteZerodhaConnection(UUID userId) { deleted = true; connection = Optional.empty(); }
        @Override
        public boolean deleteZerodhaConnection(UUID userId, String encryptedAccessToken) {
            if (connection.isPresent() && connection.get().encryptedAccessToken().equals(encryptedAccessToken)) {
                deleteZerodhaConnection(userId); return true;
            }
            return false;
        }
    }

    private static final class PassthroughCipher implements CredentialCipher {
        public String encrypt(String value) { return value; }
        public String decrypt(String value) { return value; }
    }
}
