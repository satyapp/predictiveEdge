CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.app_user (
    user_id UUID PRIMARY KEY,
    normalized_email VARCHAR(320) NOT NULL UNIQUE,
    display_email VARCHAR(320) NOT NULL,
    normalized_mobile VARCHAR(16) NOT NULL UNIQUE,
    display_mobile VARCHAR(24) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    lifecycle_state VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at TIMESTAMPTZ,
    mobile_verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_identity_mobile_e164 CHECK (normalized_mobile ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_identity_state CHECK (lifecycle_state IN ('PENDING_VERIFICATION','ACTIVE','LOCKED','SUSPENDED','CLOSED'))
);

CREATE TABLE identity.user_credential (
    user_id UUID PRIMARY KEY REFERENCES identity.app_user(user_id) ON DELETE CASCADE,
    password_hash VARCHAR(512) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity.otp_verification (
    verification_id UUID PRIMARY KEY,
    verification_session_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES identity.app_user(user_id) ON DELETE CASCADE,
    channel VARCHAR(16) NOT NULL,
    otp_hash VARCHAR(128) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    superseded_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_identity_otp_channel CHECK (channel IN ('EMAIL','MOBILE')),
    CONSTRAINT ck_identity_otp_expiry CHECK (expires_at > issued_at)
);

CREATE UNIQUE INDEX uq_identity_active_otp ON identity.otp_verification(user_id, channel)
    WHERE consumed_at IS NULL AND superseded_at IS NULL;
CREATE INDEX ix_identity_otp_session ON identity.otp_verification(verification_session_id, channel, expires_at);

CREATE TABLE identity.auth_session (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES identity.app_user(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT ck_identity_session_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_identity_active_session ON identity.auth_session(user_id, expires_at) WHERE revoked_at IS NULL;
