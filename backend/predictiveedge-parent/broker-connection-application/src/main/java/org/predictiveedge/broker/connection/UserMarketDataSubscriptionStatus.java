package org.predictiveedge.broker.connection;

import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataStreamState;

/** Immutable view of one user's current broker market-data subscription. */
public record UserMarketDataSubscriptionStatus(
        UUID userId,
        String brokerAccountId,
        LiveMarketDataSubscription subscription,
        MarketDataStreamState state) {

    public UserMarketDataSubscriptionStatus {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        Objects.requireNonNull(subscription, "Subscription is required");
        Objects.requireNonNull(state, "Stream state is required");
    }
}
