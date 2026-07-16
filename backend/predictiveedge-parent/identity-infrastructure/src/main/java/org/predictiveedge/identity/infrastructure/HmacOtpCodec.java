package org.predictiveedge.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.predictiveedge.identity.application.IdentityPorts.OtpCodec;
import org.predictiveedge.identity.domain.OtpChannel;

final class HmacOtpCodec implements OtpCodec {
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    HmacOtpCodec(String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("OTP secret must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String generate() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }

    @Override
    public String hash(UUID verificationSessionId, OtpChannel channel, String otp) {
        return hmac(verificationSessionId + ":" + channel + ":" + otp);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to calculate OTP digest", failure);
        }
    }
}
