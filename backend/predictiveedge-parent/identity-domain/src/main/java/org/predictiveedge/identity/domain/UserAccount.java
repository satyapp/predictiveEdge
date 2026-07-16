package org.predictiveedge.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        String email,
        String mobileNumber,
        String displayName,
        UserState state,
        Instant emailVerifiedAt,
        Instant mobileVerifiedAt) {

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean mobileVerified() {
        return mobileVerifiedAt != null;
    }
}
