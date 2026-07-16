package org.predictiveedge.identity.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.predictiveedge.identity.domain.OtpChannel;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;

public final class IdentityPorts {
    private IdentityPorts() {}

    public interface Store {
        RegistrationRecord createRegistration(NewRegistration registration);
        VerificationOutcome verifyOtp(UUID verificationSessionId, OtpChannel channel, String submittedHash, Instant now, int maxAttempts);
        Optional<ResendTarget> reissueOtp(UUID verificationSessionId, OtpChannel channel, String otpHash, Instant issuedAt, Instant expiresAt);
        Optional<LoginRecord> findLoginByEmail(String normalizedEmail);
        void createSession(UUID sessionId, UUID userId, Instant createdAt, Instant expiresAt);
        Optional<UserAccount> findActiveSessionUser(UUID sessionId, UUID userId, Instant now);
        void revokeSession(UUID sessionId, Instant now);
    }

    public interface PasswordHasher {
        String hash(String rawPassword);
        boolean matches(String rawPassword, String encodedPassword);
    }

    public interface OtpCodec {
        String generate();
        String hash(UUID verificationSessionId, OtpChannel channel, String otp);
    }

    public interface AccessTokenCodec {
        String issue(UUID userId, UUID sessionId, Instant expiresAt);
        TokenClaims verify(String token, Instant now);
    }

    public interface EmailOtpSender {
        void send(String email, String displayName, String otp, int expiresInMinutes);
    }

    public interface SmsOtpSender {
        DeliveryReceipt send(String mobileNumber, String otp, int expiresInMinutes);
    }

    public record NewRegistration(
            UUID userId,
            UUID verificationSessionId,
            String normalizedEmail,
            String displayEmail,
            String normalizedMobile,
            String displayMobile,
            String displayName,
            String passwordHash,
            String emailOtpHash,
            String mobileOtpHash,
            Instant issuedAt,
            Instant expiresAt) {}

    public record RegistrationRecord(UUID userId, UUID verificationSessionId, boolean deliveryRequired) {}

    public record LoginRecord(UUID userId, String passwordHash, UserState state, UserAccount user) {}

    public record TokenClaims(UUID userId, UUID sessionId, Instant expiresAt) {}

    public record DeliveryReceipt(String providerMessageId, String developmentOtp) {
        public static DeliveryReceipt accepted(String providerMessageId) {
            return new DeliveryReceipt(providerMessageId, null);
        }
    }

    public record ResendTarget(String email, String mobileNumber, String displayName) {}

    public enum VerificationOutcome {
        VERIFIED,
        INVALID,
        EXPIRED,
        ATTEMPTS_EXCEEDED
    }
}
