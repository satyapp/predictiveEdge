package org.predictiveedge.identity.infrastructure;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.predictiveedge.identity.application.IdentityPorts.LoginRecord;
import org.predictiveedge.identity.application.IdentityPorts.NewRegistration;
import org.predictiveedge.identity.application.IdentityPorts.RegistrationRecord;
import org.predictiveedge.identity.application.IdentityPorts.ResendTarget;
import org.predictiveedge.identity.application.IdentityPorts.Store;
import org.predictiveedge.identity.application.IdentityPorts.VerificationOutcome;
import org.predictiveedge.identity.domain.OtpChannel;
import org.predictiveedge.identity.domain.UserAccount;
import org.predictiveedge.identity.domain.UserState;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcIdentityStore implements Store {
    private final JdbcTemplate jdbc;

    public JdbcIdentityStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public RegistrationRecord createRegistration(NewRegistration registration) {
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM identity.app_user WHERE normalized_email = ? OR normalized_mobile = ?",
                Integer.class,
                registration.normalizedEmail(),
                registration.normalizedMobile());
        if (existing != null && existing > 0) {
            return new RegistrationRecord(UUID.randomUUID(), UUID.randomUUID(), false);
        }
        try {
            jdbc.update("""
                    INSERT INTO identity.app_user(
                        user_id, normalized_email, display_email, normalized_mobile, display_mobile, display_name,
                        lifecycle_state, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, 'PENDING_VERIFICATION', ?, ?)
                    """,
                    registration.userId(), registration.normalizedEmail(), registration.displayEmail(),
                    registration.normalizedMobile(), registration.displayMobile(), registration.displayName(),
                    Timestamp.from(registration.issuedAt()), Timestamp.from(registration.issuedAt()));
            jdbc.update("INSERT INTO identity.user_credential(user_id, password_hash, changed_at) VALUES (?, ?, ?)",
                    registration.userId(), registration.passwordHash(), Timestamp.from(registration.issuedAt()));
            insertOtp(registration, OtpChannel.EMAIL, registration.emailOtpHash());
            insertOtp(registration, OtpChannel.MOBILE, registration.mobileOtpHash());
            return new RegistrationRecord(registration.userId(), registration.verificationSessionId(), true);
        } catch (DuplicateKeyException race) {
            return new RegistrationRecord(UUID.randomUUID(), UUID.randomUUID(), false);
        }
    }

    private void insertOtp(NewRegistration registration, OtpChannel channel, String hash) {
        jdbc.update("""
                INSERT INTO identity.otp_verification(
                    verification_id, verification_session_id, user_id, channel, otp_hash, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), registration.verificationSessionId(), registration.userId(), channel.name(), hash,
                Timestamp.from(registration.issuedAt()), Timestamp.from(registration.expiresAt()));
    }

    @Override
    @Transactional
    public VerificationOutcome verifyOtp(
            UUID verificationSessionId,
            OtpChannel channel,
            String submittedHash,
            Instant now,
            int maxAttempts) {
        var challenges = jdbc.query("""
                SELECT verification_id, user_id, otp_hash, expires_at, consumed_at, superseded_at, attempt_count
                FROM identity.otp_verification
                WHERE verification_session_id = ? AND channel = ?
                ORDER BY issued_at DESC LIMIT 1 FOR UPDATE
                """,
                (rs, row) -> new OtpRow(
                        rs.getObject("verification_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("otp_hash"),
                        rs.getTimestamp("expires_at").toInstant(),
                        rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
                        rs.getTimestamp("superseded_at") == null ? null : rs.getTimestamp("superseded_at").toInstant(),
                        rs.getInt("attempt_count")),
                verificationSessionId, channel.name());
        if (challenges.isEmpty()) return VerificationOutcome.INVALID;
        OtpRow challenge = challenges.get(0);
        if (challenge.consumedAt() != null || challenge.supersededAt() != null) return VerificationOutcome.INVALID;
        if (!challenge.expiresAt().isAfter(now)) return VerificationOutcome.EXPIRED;
        if (challenge.attemptCount() >= maxAttempts) return VerificationOutcome.ATTEMPTS_EXCEEDED;
        if (!MessageDigest.isEqual(
                challenge.otpHash().getBytes(StandardCharsets.US_ASCII),
                submittedHash.getBytes(StandardCharsets.US_ASCII))) {
            int attempts = challenge.attemptCount() + 1;
            jdbc.update("UPDATE identity.otp_verification SET attempt_count = ? WHERE verification_id = ?", attempts, challenge.verificationId());
            return attempts >= maxAttempts ? VerificationOutcome.ATTEMPTS_EXCEEDED : VerificationOutcome.INVALID;
        }

        jdbc.update("UPDATE identity.otp_verification SET consumed_at = ? WHERE verification_id = ?",
                Timestamp.from(now), challenge.verificationId());
        String verifiedColumn = channel == OtpChannel.EMAIL ? "email_verified_at" : "mobile_verified_at";
        jdbc.update("UPDATE identity.app_user SET " + verifiedColumn + " = ?, updated_at = ? WHERE user_id = ?",
                Timestamp.from(now), Timestamp.from(now), challenge.userId());
        jdbc.update("""
                UPDATE identity.app_user
                SET lifecycle_state = 'ACTIVE', updated_at = ?
                WHERE user_id = ? AND email_verified_at IS NOT NULL AND mobile_verified_at IS NOT NULL
                """, Timestamp.from(now), challenge.userId());
        return VerificationOutcome.VERIFIED;
    }

    @Override
    @Transactional
    public Optional<ResendTarget> reissueOtp(
            UUID verificationSessionId,
            OtpChannel channel,
            String otpHash,
            Instant issuedAt,
            Instant expiresAt) {
        var targets = jdbc.query("""
                SELECT o.verification_id, o.user_id, u.normalized_email, u.normalized_mobile, u.display_name
                FROM identity.otp_verification o JOIN identity.app_user u ON u.user_id = o.user_id
                WHERE o.verification_session_id = ? AND o.channel = ?
                  AND o.consumed_at IS NULL AND o.superseded_at IS NULL
                ORDER BY o.issued_at DESC LIMIT 1 FOR UPDATE
                """, (rs, row) -> new ResendRow(
                        rs.getObject("verification_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        new ResendTarget(rs.getString("normalized_email"), rs.getString("normalized_mobile"), rs.getString("display_name"))),
                verificationSessionId, channel.name());
        if (targets.isEmpty()) return Optional.empty();
        ResendRow row = targets.get(0);
        jdbc.update("UPDATE identity.otp_verification SET superseded_at = ? WHERE verification_id = ?",
                Timestamp.from(issuedAt), row.verificationId());
        jdbc.update("""
                INSERT INTO identity.otp_verification(
                    verification_id, verification_session_id, user_id, channel, otp_hash, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), verificationSessionId, row.userId(), channel.name(), otpHash,
                Timestamp.from(issuedAt), Timestamp.from(expiresAt));
        return Optional.of(row.target());
    }

    @Override
    public Optional<LoginRecord> findLoginByEmail(String normalizedEmail) {
        return jdbc.query("""
                SELECT u.user_id, u.display_email, u.display_mobile, u.display_name, u.lifecycle_state,
                       u.email_verified_at, u.mobile_verified_at, c.password_hash
                FROM identity.app_user u JOIN identity.user_credential c ON c.user_id = u.user_id
                WHERE u.normalized_email = ?
                """, (rs, row) -> {
                    UserAccount user = mapUser(rs);
                    return new LoginRecord(user.id(), rs.getString("password_hash"), user.state(), user);
                }, normalizedEmail).stream().findFirst();
    }

    @Override
    public void createSession(UUID sessionId, UUID userId, Instant createdAt, Instant expiresAt) {
        jdbc.update("INSERT INTO identity.auth_session(session_id, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)",
                sessionId, userId, Timestamp.from(createdAt), Timestamp.from(expiresAt));
    }

    @Override
    public Optional<UserAccount> findActiveSessionUser(UUID sessionId, UUID userId, Instant now) {
        return jdbc.query("""
                SELECT u.user_id, u.display_email, u.display_mobile, u.display_name, u.lifecycle_state,
                       u.email_verified_at, u.mobile_verified_at
                FROM identity.auth_session s JOIN identity.app_user u ON u.user_id = s.user_id
                WHERE s.session_id = ? AND s.user_id = ? AND s.revoked_at IS NULL AND s.expires_at > ?
                """, (rs, row) -> mapUser(rs), sessionId, userId, Timestamp.from(now)).stream().findFirst();
    }

    @Override
    public void revokeSession(UUID sessionId, Instant now) {
        jdbc.update("UPDATE identity.auth_session SET revoked_at = COALESCE(revoked_at, ?) WHERE session_id = ?",
                Timestamp.from(now), sessionId);
    }

    private static UserAccount mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp emailVerified = rs.getTimestamp("email_verified_at");
        Timestamp mobileVerified = rs.getTimestamp("mobile_verified_at");
        return new UserAccount(
                rs.getObject("user_id", UUID.class),
                rs.getString("display_email"),
                rs.getString("display_mobile"),
                rs.getString("display_name"),
                UserState.valueOf(rs.getString("lifecycle_state")),
                emailVerified == null ? null : emailVerified.toInstant(),
                mobileVerified == null ? null : mobileVerified.toInstant());
    }

    private record OtpRow(
            UUID verificationId,
            UUID userId,
            String otpHash,
            Instant expiresAt,
            Instant consumedAt,
            Instant supersededAt,
            int attemptCount) {}
    private record ResendRow(UUID verificationId, UUID userId, ResendTarget target) {}
}
