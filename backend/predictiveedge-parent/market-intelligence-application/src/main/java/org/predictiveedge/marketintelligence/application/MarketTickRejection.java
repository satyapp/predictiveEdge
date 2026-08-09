package org.predictiveedge.marketintelligence.application;

import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.MarketTick;

/** Auditable reason why a normalized tick was not admitted to canonical aggregation. */
public record MarketTickRejection(
        UUID userId,
        String brokerAccountId,
        MarketTick tick,
        Reason reason,
        String detail) {

    public MarketTickRejection {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        Objects.requireNonNull(tick, "Rejected tick is required");
        Objects.requireNonNull(reason, "Rejection reason is required");
        if (detail == null || detail.isBlank()) throw new IllegalArgumentException("Rejection detail is required");
        detail = detail.trim();
    }

    public enum Reason {
        DUPLICATE,
        SESSION_UNAVAILABLE,
        OUTSIDE_CONTINUOUS_TRADING,
        INVALID_CUMULATIVE_VOLUME
    }
}
