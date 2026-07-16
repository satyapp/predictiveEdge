package org.predictiveedge.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.predictiveedge.identity.application.IdentityPorts.AccessTokenCodec;
import org.predictiveedge.identity.application.IdentityPorts.TokenClaims;
import org.predictiveedge.identity.domain.IdentityFailure;

final class HmacAccessTokenCodec implements AccessTokenCodec {
    private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
    private final byte[] secret;

    HmacAccessTokenCodec(String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("Access-token secret must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String issue(UUID userId, UUID sessionId, Instant expiresAt) {
        String payload = encode(userId + ":" + sessionId + ":" + expiresAt.getEpochSecond());
        String unsigned = HEADER + "." + payload;
        return unsigned + "." + encode(sign(unsigned));
    }

    @Override
    public TokenClaims verify(String token, Instant now) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !HEADER.equals(parts[0])) throw invalid();
            String unsigned = parts[0] + "." + parts[1];
            if (!MessageDigest.isEqual(sign(unsigned), Base64.getUrlDecoder().decode(parts[2]))) throw invalid();
            String[] claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).split(":");
            if (claims.length != 3) throw invalid();
            TokenClaims parsed = new TokenClaims(UUID.fromString(claims[0]), UUID.fromString(claims[1]), Instant.ofEpochSecond(Long.parseLong(claims[2])));
            if (!parsed.expiresAt().isAfter(now)) throw invalid();
            return parsed;
        } catch (IdentityFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to sign access token", failure);
        }
    }

    private static String encode(String value) { return encode(value.getBytes(StandardCharsets.UTF_8)); }
    private static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static IdentityFailure invalid() { return new IdentityFailure(IdentityFailure.Code.ACCESS_TOKEN_INVALID, "Authentication is required."); }
}
