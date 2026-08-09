package org.predictiveedge.broker.connection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataInstrumentResolver;

/** Resolves user-selected instruments and delegates their live-stream lifecycle to the manager. */
public final class UserMarketDataSubscriptionService {
    private final LiveMarketDataInstrumentResolver instruments;
    private final UserMarketDataSubscriptionManager subscriptions;

    public UserMarketDataSubscriptionService(
            LiveMarketDataInstrumentResolver instruments,
            UserMarketDataSubscriptionManager subscriptions) {
        this.instruments = Objects.requireNonNull(instruments, "Instrument resolver is required");
        this.subscriptions = Objects.requireNonNull(subscriptions, "Subscription manager is required");
    }

    public UserMarketDataSubscriptionStatus subscribe(
            BrokerContext context,
            List<Instrument> requestedInstruments,
            UserMarketDataListener listener) {
        var resolved = instruments.resolve(context, requestedInstruments);
        return subscriptions.subscribe(context,
                new LiveMarketDataSubscription(resolved, MarketDataDetail.FULL), listener);
    }

    public boolean unsubscribe(UUID userId) {
        return subscriptions.unsubscribe(userId);
    }

    public Optional<UserMarketDataSubscriptionStatus> status(UUID userId) {
        return subscriptions.status(userId);
    }
}
