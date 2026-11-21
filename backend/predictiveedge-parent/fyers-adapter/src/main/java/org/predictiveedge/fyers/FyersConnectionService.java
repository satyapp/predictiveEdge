package org.predictiveedge.fyers;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.predictiveedge.broker.connection.CredentialCipher;

public final class FyersConnectionService {
    private static final ZoneId FYERS_ZONE = ZoneId.of("Asia/Kolkata");
    private final FyersConnectionStore store;
    private final CredentialCipher cipher;
    private final FyersHttpClient client;
    private final Settings settings;
    private final Clock clock;
    private final SecureRandom random;
    FyersConnectionService(FyersConnectionStore store, CredentialCipher cipher, FyersHttpClient client,
            Settings settings, Clock clock, SecureRandom random) {
        this.store = store; this.cipher = cipher; this.client = client; this.settings = settings;
        this.clock = clock; this.random = random;
    }
    public Status status(UUID userId) {
        var stored = store.find(userId);
        if (stored.isPresent() && !clock.instant().isBefore(effectiveExpiry(stored.get()))) {
            store.delete(userId); stored = java.util.Optional.empty();
        }
        return new Status(settings.configured(), stored.isPresent(),
                stored.map(FyersConnectionStore.Stored::accountId).orElse(null),
                stored.map(FyersConnectionStore.Stored::connectedAt).orElse(null),
                stored.map(FyersConnectionService::effectiveExpiry).orElse(null), settings.redirectUri());
    }
    public URI begin(UUID userId, String predictiveEdgeSession) {
        requireConfigured();
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        store.createState(hash(state), userId, hash(predictiveEdgeSession), clock.instant().plus(Duration.ofMinutes(10)));
        return URI.create("https://api-t1.fyers.in/api/v3/generate-authcode?client_id=" + encode(settings.appId())
                + "&redirect_uri=" + encode(settings.redirectUri()) + "&response_type=code&state=" + encode(state));
    }
    public URI complete(String state, String authCode) {
        requireConfigured();
        var pending = store.consumeState(hash(state), clock.instant())
                .orElseThrow(() -> new IllegalStateException("FYERS connection state is invalid or expired"));
        var token = client.exchange(hash(settings.appId() + ":" + settings.secretId()), authCode);
        String accessToken = required(token, "access_token");
        var profile = client.profile(settings.appId() + ":" + accessToken);
        String account = profile.path("data").path("fy_id").asText();
        if (account.isBlank()) account = profile.path("data").path("name").asText("FYERS");
        Instant now = clock.instant();
        store.save(pending.userId(), account, cipher.encrypt(accessToken), pending.sessionHash(), now,
                tokenExpiry(now));
        return URI.create(settings.webBaseUrl() + (settings.webBaseUrl().contains("?") ? "&" : "?")
                + "provider=fyers&connected=true");
    }
    public void disconnect(UUID userId) { store.delete(userId); }
    String authorization(UUID userId) {
        var stored = store.find(userId).orElseThrow(() -> new IllegalStateException("Connect FYERS first"));
        if (!clock.instant().isBefore(effectiveExpiry(stored))) { store.delete(userId); throw new IllegalStateException("FYERS session has expired; reconnect FYERS"); }
        return settings.appId() + ":" + cipher.decrypt(stored.encryptedToken());
    }
    public Verification verifyDataPath(UUID userId) {
        String auth = authorization(userId);
        var profile = client.profile(auth);
        Instant now = clock.instant();
        var parameters = new LinkedHashMap<String, String>();
        parameters.put("symbol", "NSE:NIFTY50-INDEX");
        parameters.put("resolution", "D");
        parameters.put("date_format", "0");
        parameters.put("range_from", Long.toString(now.minus(Duration.ofDays(10)).getEpochSecond()));
        parameters.put("range_to", Long.toString(now.getEpochSecond()));
        parameters.put("cont_flag", "0");
        parameters.put("oi_flag", "0");
        var history = client.history(auth, parameters);
        var candles = history.path("candles");
        if (!candles.isArray() || candles.isEmpty())
            throw new IllegalStateException("FYERS History API returned no NIFTY 50 daily candles");
        var last = candles.get(candles.size() - 1);
        return new Verification("ok", profile.path("s").asText("ok"), "NSE:NIFTY50-INDEX",
                candles.size(), Instant.ofEpochSecond(last.get(0).longValue()), now);
    }
    static Instant tokenExpiry(Instant issuedAt) {
        ZonedDateTime local = issuedAt.atZone(FYERS_ZONE);
        ZonedDateTime cutoff = local.toLocalDate().atTime(6, 30).atZone(FYERS_ZONE);
        if (!issuedAt.isBefore(cutoff.toInstant())) cutoff = cutoff.plusDays(1);
        return cutoff.toInstant();
    }
    private static Instant effectiveExpiry(FyersConnectionStore.Stored stored) {
        Instant documented = tokenExpiry(stored.connectedAt());
        return stored.expiresAt().isBefore(documented) ? stored.expiresAt() : documented;
    }
    private void requireConfigured() { if (!settings.configured()) throw new IllegalStateException("FYERS is not configured"); }
    private static String required(com.fasterxml.jackson.databind.JsonNode node, String name) {
        String value = node.path(name).asText(); if (value.isBlank()) throw new IllegalStateException("FYERS did not return " + name); return value;
    }
    private static String hash(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Session value is required");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public record Settings(boolean enabled, String appId, String secretId, String redirectUri, String webBaseUrl) {
        boolean configured() { return enabled && appId != null && !appId.isBlank() && secretId != null && !secretId.isBlank()
                && redirectUri != null && !redirectUri.isBlank(); }
    }
    public record Status(boolean configured, boolean connected, String accountId, Instant connectedAt,
            Instant expiresAt, String redirectUri) { }
    public record Verification(String status, String profileStatus, String symbol, int candleCount,
            Instant latestCandleAt, Instant checkedAt) { }
}
