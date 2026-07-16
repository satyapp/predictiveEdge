package org.predictiveedge.identity.application;

import static org.predictiveedge.identity.application.IdentityPorts.VerificationOutcome.ATTEMPTS_EXCEEDED;
import static org.predictiveedge.identity.application.IdentityPorts.VerificationOutcome.EXPIRED;
import static org.predictiveedge.identity.application.IdentityPorts.VerificationOutcome.INVALID;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.predictiveedge.identity.application.IdentityPorts.AccessTokenCodec;
import org.predictiveedge.identity.application.IdentityPorts.DeliveryReceipt;
import org.predictiveedge.identity.application.IdentityPorts.EmailOtpSender;
import org.predictiveedge.identity.application.IdentityPorts.LoginRecord;
import org.predictiveedge.identity.application.IdentityPorts.NewRegistration;
import org.predictiveedge.identity.application.IdentityPorts.OtpCodec;
import org.predictiveedge.identity.application.IdentityPorts.PasswordHasher;
import org.predictiveedge.identity.application.IdentityPorts.RegistrationRecord;
import org.predictiveedge.identity.application.IdentityPorts.ResendTarget;
import org.predictiveedge.identity.application.IdentityPorts.SmsOtpSender;
import org.predictiveedge.identity.application.IdentityPorts.Store;
import org.predictiveedge.identity.application.IdentityPorts.TokenClaims;
import org.predictiveedge.identity.domain.IdentityFailure;
import org.predictiveedge.identity.domain.OtpChannel;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;
import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    private final Store store;
    private final PasswordHasher passwordHasher;
    private final OtpCodec otpCodec;
    private final AccessTokenCodec accessTokenCodec;
    private final EmailOtpSender emailSender;
    private final SmsOtpSender smsSender;
    private final IdentityPolicy policy;
    private final Clock clock;
    private final String dummyPasswordHash;

    public IdentityService(
            Store store,
            PasswordHasher passwordHasher,
            OtpCodec otpCodec,
            AccessTokenCodec accessTokenCodec,
            EmailOtpSender emailSender,
            SmsOtpSender smsSender,
            IdentityPolicy policy,
            Clock clock) {
        this.store = store;
        this.passwordHasher = passwordHasher;
        this.otpCodec = otpCodec;
        this.accessTokenCodec = accessTokenCodec;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.policy = policy;
        this.clock = clock;
        this.dummyPasswordHash = passwordHasher.hash(UUID.randomUUID().toString());
    }

    public RegistrationResult register(RegisterCommand command) {
        var email = normalizeEmail(command.email());
        var mobile = command.mobileNumber().trim();
        var sessionId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var emailOtp = otpCodec.generate();
        var mobileOtp = otpCodec.generate();
        var now = clock.instant();
        var expiresAt = now.plus(policy.otpLifetime());

        RegistrationRecord created = store.createRegistration(new NewRegistration(
                userId,
                sessionId,
                email,
                command.email().trim(),
                mobile,
                mobile,
                command.displayName().trim(),
                passwordHasher.hash(command.password()),
                otpCodec.hash(sessionId, OtpChannel.EMAIL, emailOtp),
                otpCodec.hash(sessionId, OtpChannel.MOBILE, mobileOtp),
                now,
                expiresAt));

        String developmentMobileOtp = null;
        String deliveryWarning = null;
        if (created.deliveryRequired()) {
            try {
                emailSender.send(email, command.displayName().trim(), emailOtp, (int) policy.otpLifetime().toMinutes());
            } catch (RuntimeException failure) {
                deliveryWarning = "Email OTP delivery is pending. Check SES configuration, then resend the email OTP.";
            }
            try {
                DeliveryReceipt smsReceipt = smsSender.send(mobile, mobileOtp, (int) policy.otpLifetime().toMinutes());
                developmentMobileOtp = smsReceipt.developmentOtp();
            } catch (RuntimeException failure) {
                deliveryWarning = deliveryWarning == null
                        ? "Mobile OTP delivery is pending. Please resend the mobile OTP."
                        : deliveryWarning + " Mobile OTP delivery is also pending.";
            }
        }

        return new RegistrationResult(
                created.verificationSessionId(),
                maskEmail(command.email()),
                maskMobile(mobile),
                developmentMobileOtp,
                policy.otpLifetime().toSeconds(),
                deliveryWarning);
    }

    public ResendResult resendOtp(UUID verificationSessionId, OtpChannel channel) {
        String otp = otpCodec.generate();
        Instant now = clock.instant();
        ResendTarget target = store.reissueOtp(
                verificationSessionId,
                channel,
                otpCodec.hash(verificationSessionId, channel, otp),
                now,
                now.plus(policy.otpLifetime()))
                .orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.OTP_INVALID, "The verification session is invalid."));
        try {
            if (channel == OtpChannel.EMAIL) {
                emailSender.send(target.email(), target.displayName(), otp, (int) policy.otpLifetime().toMinutes());
                return new ResendResult(null, policy.otpLifetime().toSeconds());
            }
            DeliveryReceipt receipt = smsSender.send(target.mobileNumber(), otp, (int) policy.otpLifetime().toMinutes());
            return new ResendResult(receipt.developmentOtp(), policy.otpLifetime().toSeconds());
        } catch (RuntimeException failure) {
            throw new IdentityFailure(IdentityFailure.Code.DEPENDENCY_UNAVAILABLE,
                    "Verification delivery is temporarily unavailable.");
        }
    }

    public void verifyOtp(UUID verificationSessionId, OtpChannel channel, String otp) {
        var submittedHash = otpCodec.hash(verificationSessionId, channel, otp);
        var outcome = store.verifyOtp(
                verificationSessionId,
                channel,
                submittedHash,
                clock.instant(),
                policy.otpMaxAttempts());
        if (outcome == INVALID) {
            throw new IdentityFailure(IdentityFailure.Code.OTP_INVALID, "The verification code is invalid.");
        }
        if (outcome == EXPIRED) {
            throw new IdentityFailure(IdentityFailure.Code.OTP_EXPIRED, "The verification code has expired.");
        }
        if (outcome == ATTEMPTS_EXCEEDED) {
            throw new IdentityFailure(IdentityFailure.Code.OTP_ATTEMPTS_EXCEEDED, "Too many verification attempts.");
        }
    }

    public AuthResult login(LoginCommand command) {
        Optional<LoginRecord> candidate = store.findLoginByEmail(normalizeEmail(command.email()));
        boolean passwordMatches = passwordHasher.matches(
                command.password(),
                candidate.map(LoginRecord::passwordHash).orElse(dummyPasswordHash));
        if (candidate.isEmpty() || !passwordMatches) {
            throw invalidCredentials();
        }
        LoginRecord login = candidate.orElseThrow();
        if (login.state() != UserState.ACTIVE) {
            throw new IdentityFailure(IdentityFailure.Code.ACCOUNT_UNAVAILABLE,
                    "Complete email and mobile verification before signing in.");
        }

        var now = clock.instant();
        var expiresAt = now.plus(policy.accessTokenLifetime());
        var sessionId = UUID.randomUUID();
        store.createSession(sessionId, login.userId(), now, expiresAt);
        var token = accessTokenCodec.issue(login.userId(), sessionId, expiresAt);
        return new AuthResult(token, policy.accessTokenLifetime().toSeconds(), sessionId, login.user());
    }

    public AuthenticatedIdentity authenticate(String token) {
        TokenClaims claims = accessTokenCodec.verify(token, clock.instant());
        UserAccount user = store.findActiveSessionUser(claims.sessionId(), claims.userId(), clock.instant())
                .orElseThrow(() -> new IdentityFailure(IdentityFailure.Code.ACCESS_TOKEN_INVALID, "Authentication is required."));
        return new AuthenticatedIdentity(claims.sessionId(), user);
    }

    public void logout(AuthenticatedIdentity identity) {
        store.revokeSession(identity.sessionId(), clock.instant());
    }

    private static IdentityFailure invalidCredentials() {
        return new IdentityFailure(IdentityFailure.Code.INVALID_CREDENTIALS, "Email or password is incorrect.");
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String maskEmail(String email) {
        String normalized = email.trim();
        int at = normalized.indexOf('@');
        if (at <= 1) return "***" + normalized.substring(Math.max(0, at));
        return normalized.substring(0, 1) + "***" + normalized.substring(at);
    }

    private static String maskMobile(String mobile) {
        if (mobile.length() <= 4) return "****";
        return "*".repeat(mobile.length() - 4) + mobile.substring(mobile.length() - 4);
    }

    public record RegisterCommand(String displayName, String email, String mobileNumber, String password) {}
    public record LoginCommand(String email, String password) {}
    public record RegistrationResult(
            UUID verificationSessionId,
            String maskedEmail,
            String maskedMobileNumber,
            String developmentMobileOtp,
            long expiresInSeconds,
            String deliveryWarning) {}
    public record ResendResult(String developmentOtp, long expiresInSeconds) {}
    public record AuthResult(String accessToken, long expiresIn, UUID sessionId, UserAccount user) {}
    public record AuthenticatedIdentity(UUID sessionId, UserAccount user) {}
}
