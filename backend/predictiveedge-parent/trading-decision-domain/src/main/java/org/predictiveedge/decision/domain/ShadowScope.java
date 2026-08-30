package org.predictiveedge.decision.domain;

import java.util.Objects;
import java.util.UUID;

/** Fixed personal-use boundary: exactly one user and one equity in shadow mode. */
public record ShadowScope(UUID userId, InstrumentRef instrument) {
    public ShadowScope {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
    }

    public void requireMatches(UUID candidateUserId, InstrumentRef candidateInstrument) {
        if (!userId.equals(candidateUserId) || !instrument.equals(candidateInstrument)) {
            throw new IllegalArgumentException("Shadow evaluation is outside the configured one-user/one-equity scope");
        }
    }
}
