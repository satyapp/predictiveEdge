package org.predictiveedge.broker.connection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.predictiveedge.broker.domain.LiveMarketDataSubscription;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.domain.MarketTick;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataListener;
import org.predictiveedge.broker.spi.LiveMarketDataProvider;
import org.predictiveedge.broker.spi.LiveMarketDataStream;

/** Owns at most one active broker market-data stream for each PredictiveEdge user. */
public final class UserMarketDataSubscriptionManager implements AutoCloseable {
    private final LiveMarketDataProvider provider;
    private final Map<UUID, ManagedSubscription> subscriptions = new LinkedHashMap<>();
    private boolean closed;

    public UserMarketDataSubscriptionManager(LiveMarketDataProvider provider) {
        this.provider = Objects.requireNonNull(provider, "Live market-data provider is required");
    }

    /**
     * Starts or replaces a user's stream. Repeating the same request with the same listener is idempotent
     * while the current stream is non-terminal.
     */
    public synchronized UserMarketDataSubscriptionStatus subscribe(
            BrokerContext context,
            LiveMarketDataSubscription subscription,
            UserMarketDataListener listener) {
        ensureOpen();
        Objects.requireNonNull(context, "Broker context is required");
        Objects.requireNonNull(subscription, "Subscription is required");
        Objects.requireNonNull(listener, "User market-data listener is required");

        var current = subscriptions.get(context.userId());
        if (current != null && current.matches(context, subscription, listener) && !current.terminal())
            return current.status();

        if (current != null) {
            subscriptions.remove(context.userId());
            current.deactivate();
            current.closeStream();
        }

        var managed = new ManagedSubscription(context, subscription, listener);
        subscriptions.put(context.userId(), managed);
        try {
            managed.attach(provider.connect(context, subscription, managed));
            return managed.status();
        } catch (RuntimeException failure) {
            subscriptions.remove(context.userId(), managed);
            managed.deactivate();
            managed.closeStream();
            throw failure;
        }
    }

    /** Stops a user's stream; repeated stops are safe. */
    public synchronized boolean unsubscribe(UUID userId) {
        Objects.requireNonNull(userId, "User id is required");
        var managed = subscriptions.remove(userId);
        if (managed == null) return false;
        managed.deactivate();
        managed.closeStream();
        managed.listener.onStateChanged(userId, managed.context.brokerAccountId(), MarketDataStreamState.CLOSED);
        return true;
    }

    public synchronized Optional<UserMarketDataSubscriptionStatus> status(UUID userId) {
        Objects.requireNonNull(userId, "User id is required");
        return Optional.ofNullable(subscriptions.get(userId)).map(ManagedSubscription::status);
    }

    public synchronized List<UserMarketDataSubscriptionStatus> statuses() {
        return subscriptions.values().stream().map(ManagedSubscription::status).toList();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        var active = new ArrayList<>(subscriptions.values());
        subscriptions.clear();
        active.forEach(ManagedSubscription::deactivate);
        active.forEach(ManagedSubscription::closeStream);
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("User market-data subscription manager is closed");
    }

    private final class ManagedSubscription implements LiveMarketDataListener {
        private final BrokerContext context;
        private final LiveMarketDataSubscription subscription;
        private final UserMarketDataListener listener;
        private volatile MarketDataStreamState state = MarketDataStreamState.CONNECTING;
        private LiveMarketDataStream stream;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private ManagedSubscription(BrokerContext context, LiveMarketDataSubscription subscription,
                UserMarketDataListener listener) {
            this.context = context;
            this.subscription = subscription;
            this.listener = listener;
        }

        private void attach(LiveMarketDataStream stream) {
            this.stream = Objects.requireNonNull(stream, "Live market-data stream is required");
            this.state = stream.state();
        }

        private boolean matches(BrokerContext requestedContext, LiveMarketDataSubscription requestedSubscription,
                UserMarketDataListener requestedListener) {
            return context.equals(requestedContext) && subscription.equals(requestedSubscription)
                    && listener == requestedListener;
        }

        private boolean terminal() {
            return state == MarketDataStreamState.CLOSED || state == MarketDataStreamState.FAILED;
        }

        private UserMarketDataSubscriptionStatus status() {
            return new UserMarketDataSubscriptionStatus(context.userId(), context.brokerAccountId(),
                    subscription, state);
        }

        private void deactivate() {
            active.set(false);
        }

        private void closeStream() {
            if (stream != null) stream.close();
        }

        @Override
        public void onTicks(List<MarketTick> ticks) {
            Objects.requireNonNull(ticks, "Market ticks are required");
            if (!active.get()) return;
            listener.onTicks(context.userId(), context.brokerAccountId(), List.copyOf(ticks));
        }

        @Override
        public void onStateChanged(MarketDataStreamState next) {
            Objects.requireNonNull(next, "Stream state is required");
            state = next;
            if (!active.get()) return;
            listener.onStateChanged(context.userId(), context.brokerAccountId(), next);
        }

        @Override
        public void onFailure(RuntimeException failure) {
            Objects.requireNonNull(failure, "Stream failure is required");
            if (!active.get()) return;
            listener.onFailure(context.userId(), context.brokerAccountId(), failure);
        }
    }
}
