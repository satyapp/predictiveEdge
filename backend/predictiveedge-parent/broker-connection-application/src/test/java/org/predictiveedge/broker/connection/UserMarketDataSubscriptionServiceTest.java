package org.predictiveedge.broker.connection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.LiveMarketDataInstrument;
import org.predictiveedge.broker.domain.MarketDataDetail;
import org.predictiveedge.broker.domain.MarketDataInstrumentKind;
import org.predictiveedge.broker.domain.MarketDataStreamState;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.broker.spi.LiveMarketDataListener;
import org.predictiveedge.broker.spi.LiveMarketDataStream;

class UserMarketDataSubscriptionServiceTest {
    @Test
    void resolvesRequestedSymbolsIntoAFullModeManagedSubscription() {
        var context = BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD123");
        var infy = new Instrument("NSE", "INFY");
        var resolved = new LiveMarketDataInstrument(infy, "408065", MarketDataInstrumentKind.EQUITY);
        var provider = new RecordingProvider();
        var manager = new UserMarketDataSubscriptionManager(provider);
        var service = new UserMarketDataSubscriptionService(
                (requestedContext, instruments) -> {
                    assertThat(requestedContext).isEqualTo(context);
                    assertThat(instruments).containsExactly(infy);
                    return List.of(resolved);
                }, manager);

        var status = service.subscribe(context, List.of(infy), new NoOpUserListener());

        assertThat(status.subscription().detail()).isEqualTo(MarketDataDetail.FULL);
        assertThat(status.subscription().instruments()).containsExactly(resolved);
        assertThat(provider.subscription).isEqualTo(status.subscription());
        assertThat(service.status(context.userId())).contains(status);
        assertThat(service.unsubscribe(context.userId())).isTrue();
    }

    private static final class RecordingProvider implements org.predictiveedge.broker.spi.LiveMarketDataProvider {
        private org.predictiveedge.broker.domain.LiveMarketDataSubscription subscription;

        @Override
        public LiveMarketDataStream connect(BrokerContext context,
                org.predictiveedge.broker.domain.LiveMarketDataSubscription subscription,
                LiveMarketDataListener listener) {
            this.subscription = subscription;
            listener.onStateChanged(MarketDataStreamState.CONNECTING);
            return new LiveMarketDataStream() {
                @Override public MarketDataStreamState state() { return MarketDataStreamState.CONNECTING; }
                @Override public void close() {}
            };
        }
    }

    private static final class NoOpUserListener implements UserMarketDataListener {
        @Override public void onTicks(UUID userId, String accountId,
                List<org.predictiveedge.broker.domain.MarketTick> ticks) {}
        @Override public void onStateChanged(UUID userId, String accountId, MarketDataStreamState state) {}
        @Override public void onFailure(UUID userId, String accountId, RuntimeException failure) {}
    }
}
