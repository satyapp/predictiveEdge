package org.predictiveedge.marketintelligence.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionService;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionStatus;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.spi.BrokerContext;

/** Starts a user's broker stream with the canonical market-intelligence consumer attached. */
public final class UserMarketIntelligenceSubscriptionService {
    private final UserMarketDataSubscriptionService subscriptions;
    private final MarketIntelligenceTickConsumer consumer;

    public UserMarketIntelligenceSubscriptionService(
            UserMarketDataSubscriptionService subscriptions,
            MarketIntelligenceTickConsumer consumer) {
        this.subscriptions = Objects.requireNonNull(subscriptions, "Subscription service is required");
        this.consumer = Objects.requireNonNull(consumer, "Market-intelligence consumer is required");
    }

    public UserMarketDataSubscriptionStatus subscribe(BrokerContext context, List<Instrument> instruments) {
        return subscriptions.subscribe(context, instruments, consumer);
    }

    public boolean unsubscribe(UUID userId) {
        return subscriptions.unsubscribe(userId);
    }

    public Optional<UserMarketDataSubscriptionStatus> status(UUID userId) {
        return subscriptions.status(userId);
    }
}
