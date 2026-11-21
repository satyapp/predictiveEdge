package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpRequest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.connection.CredentialCipher;

class FyersConnectionServiceTest {
    private static final UUID USER = UUID.randomUUID();

    @Test
    void expiresAtTheNext0630IndiaCutoffWhenIssuedAfterCutoff() {
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-21T08:46:45Z")))
                .isEqualTo(Instant.parse("2026-11-22T01:00:00Z"));
    }

    @Test
    void expiresAtTheSameDay0630IndiaCutoffWhenIssuedBeforeCutoff() {
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-20T23:30:00Z")))
                .isEqualTo(Instant.parse("2026-11-21T01:00:00Z"));
    }

    @Test
    void tokenIssuedOneMillisecondBeforeCutoffExpiresAtThatCutoff() {
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-21T00:59:59.999Z")))
                .isEqualTo(Instant.parse("2026-11-21T01:00:00Z"));
    }

    @Test
    void tokenIssuedExactlyAtCutoffExpiresAtTheNextDaysCutoff() {
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-21T01:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-22T01:00:00Z"));
    }

    @Test
    void tokenIssuedJustBeforeIndiaMidnightExpiresAtTheNextMorningsCutoff() {
        // 23:59 IST on 21 Nov is 18:29 UTC; the next cutoff is 06:30 IST on 22 Nov.
        assertThat(FyersConnectionService.tokenExpiry(Instant.parse("2026-11-21T18:29:00Z")))
                .isEqualTo(Instant.parse("2026-11-22T01:00:00Z"));
    }

    @Test
    void statusReportsTheIndiaCutoffEvenWhenAnOlderRecordStoredA24HourExpiry() {
        Instant connected = Instant.parse("2026-11-21T08:46:45Z");
        var store = new InMemoryStore();
        store.save(USER, "FY1234", "enc:token", "session", connected, connected.plus(Duration.ofHours(24)));
        var service = service(store, new ScriptedTransport(), Instant.parse("2026-11-21T12:00:00Z"));

        var status = service.status(USER);

        assertThat(status.connected()).isTrue();
        assertThat(status.expiresAt()).isEqualTo(Instant.parse("2026-11-22T01:00:00Z"));
    }

    @Test
    void statusDropsTheConnectionOnceTheCutoffHasPassed() {
        Instant connected = Instant.parse("2026-11-21T08:46:45Z");
        var store = new InMemoryStore();
        store.save(USER, "FY1234", "enc:token", "session", connected, connected.plus(Duration.ofHours(24)));
        var service = service(store, new ScriptedTransport(), Instant.parse("2026-11-22T01:00:00Z"));

        assertThat(service.status(USER).connected()).isFalse();
        assertThat(store.find(USER)).isEmpty();
    }

    @Test
    void verificationUsesOnlyReadOnlyProfileAndHistoryCalls() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        var store = new InMemoryStore();
        store.save(USER, "FY1234", "enc:daily-token", "session", now.minus(Duration.ofHours(1)),
                FyersConnectionService.tokenExpiry(now.minus(Duration.ofHours(1))));
        var transport = new ScriptedTransport()
                .respond("{\"s\":\"ok\",\"data\":{\"fy_id\":\"FY1234\"}}")
                .respond("{\"s\":\"ok\",\"candles\":[[1763596800,1,2,0.5,1.5,0],[1763683200,1,2,0.5,1.7,0]]}");
        var service = service(store, transport, now);

        var result = service.verifyDataPath(USER);

        assertThat(result.status()).isEqualTo("ok");
        assertThat(result.profileStatus()).isEqualTo("ok");
        assertThat(result.symbol()).isEqualTo("NSE:NIFTY50-INDEX");
        assertThat(result.candleCount()).isEqualTo(2);
        assertThat(result.latestCandleAt()).isEqualTo(Instant.ofEpochSecond(1763683200L));
        assertThat(transport.requests).extracting(HttpRequest::method).containsOnly("GET");
        assertThat(transport.requests).extracting(request -> request.uri().getPath())
                .containsExactly("/api/v3/profile", "/data/history");
        assertThat(transport.requests.get(1).uri().getRawQuery())
                .contains("symbol=NSE%3ANIFTY50-INDEX", "resolution=D");
        assertThat(transport.requests).allSatisfy(request ->
                assertThat(request.headers().firstValue("Authorization")).contains("APP-100:daily-token"));
    }

    @Test
    void verificationFailsWhenHistoryReturnsNoCandles() {
        Instant now = Instant.parse("2026-11-21T10:00:00Z");
        var store = new InMemoryStore();
        store.save(USER, "FY1234", "enc:daily-token", "session", now.minus(Duration.ofHours(1)),
                FyersConnectionService.tokenExpiry(now.minus(Duration.ofHours(1))));
        var transport = new ScriptedTransport().respond("{\"s\":\"ok\"}").respond("{\"s\":\"ok\",\"candles\":[]}");

        assertThatThrownBy(() -> service(store, transport, now).verifyDataPath(USER))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no NIFTY 50 daily candles");
    }

    @Test
    void verificationRequiresReconnectAfterTheCutoffWithoutCallingFyers() {
        Instant connected = Instant.parse("2026-11-21T08:46:45Z");
        var store = new InMemoryStore();
        store.save(USER, "FY1234", "enc:daily-token", "session", connected, connected.plus(Duration.ofHours(24)));
        var transport = new ScriptedTransport();

        assertThatThrownBy(() -> service(store, transport, Instant.parse("2026-11-22T02:00:00Z")).verifyDataPath(USER))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("reconnect FYERS");
        assertThat(transport.requests).isEmpty();
    }

    private static FyersConnectionService service(FyersConnectionStore store, ScriptedTransport transport, Instant now) {
        var client = new FyersHttpClient(new ObjectMapper(), Duration.ofSeconds(5), transport);
        var settings = new FyersConnectionService.Settings(true, "APP-100", "secret", "https://example.test/callback",
                "https://example.test/app");
        return new FyersConnectionService(store, new PrefixCipher(), client, settings,
                Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());
    }

    private static final class PrefixCipher implements CredentialCipher {
        @Override public String encrypt(String plaintext) { return "enc:" + plaintext; }
        @Override public String decrypt(String ciphertext) { return ciphertext.substring("enc:".length()); }
    }

    private static final class ScriptedTransport implements FyersTransport {
        final List<HttpRequest> requests = new ArrayList<>();
        private final Deque<String> responses = new ArrayDeque<>();
        ScriptedTransport respond(String body) { responses.add(body); return this; }
        @Override public Response send(HttpRequest request) {
            requests.add(request);
            if (responses.isEmpty()) throw new AssertionError("Unexpected FYERS call: " + request.uri());
            return new Response(200, responses.poll());
        }
    }

    private static final class InMemoryStore implements FyersConnectionStore {
        private final Map<UUID, Stored> connections = new HashMap<>();
        @Override public void createState(String hash, UUID userId, String sessionHash, Instant expiresAt) { }
        @Override public Optional<Pending> consumeState(String hash, Instant now) { return Optional.empty(); }
        @Override public void save(UUID userId, String accountId, String encryptedToken, String sessionHash,
                Instant connectedAt, Instant expiresAt) {
            connections.put(userId, new Stored(accountId, encryptedToken, connectedAt, expiresAt));
        }
        @Override public Optional<Stored> find(UUID userId) { return Optional.ofNullable(connections.get(userId)); }
        @Override public void delete(UUID userId) { connections.remove(userId); }
    }
}
