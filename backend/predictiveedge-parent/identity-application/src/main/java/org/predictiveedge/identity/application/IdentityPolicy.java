package org.predictiveedge.identity.application;

import java.time.Duration;

public record IdentityPolicy(Duration otpLifetime, int otpMaxAttempts, Duration accessTokenLifetime) {
    public IdentityPolicy {
        if (otpLifetime.isNegative() || otpLifetime.isZero()) throw new IllegalArgumentException("OTP lifetime must be positive");
        if (otpMaxAttempts < 1) throw new IllegalArgumentException("OTP attempts must be positive");
        if (accessTokenLifetime.isNegative() || accessTokenLifetime.isZero()) throw new IllegalArgumentException("Access-token lifetime must be positive");
    }
}
