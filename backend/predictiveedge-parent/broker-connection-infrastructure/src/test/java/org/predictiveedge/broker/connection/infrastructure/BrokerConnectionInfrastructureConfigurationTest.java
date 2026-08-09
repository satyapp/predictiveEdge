package org.predictiveedge.broker.connection.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.spi.LiveMarketDataProvider;
import org.predictiveedge.broker.zerodha.ZerodhaSessionProvider;

class BrokerConnectionInfrastructureConfigurationTest {
    private final BrokerConnectionInfrastructureConfiguration configuration =
            new BrokerConnectionInfrastructureConfiguration();

    @Test
    void createsManagedZerodhaLiveMarketDataProvider() {
        ScheduledExecutorService scheduler = configuration.zerodhaMarketDataScheduler(1);
        ZerodhaSessionProvider sessions = context -> {
            throw new UnsupportedOperationException("not used by configuration test");
        };
        try {
            LiveMarketDataProvider provider = configuration.zerodhaLiveMarketDataProvider(
                    sessions, scheduler, new ObjectMapper(), 10, 500, 30, 8, 15, 2_097_152);
            var transport = configuration.zerodhaTransport();
            var resolver = configuration.zerodhaInstrumentResolver(sessions, transport);
            var manager = configuration.userMarketDataSubscriptionManager(provider);

            assertThat(provider).isNotNull();
            assertThat(resolver).isNotNull();
            assertThat(manager).isNotNull();
            assertThat(configuration.userMarketDataSubscriptionService(resolver, manager)).isNotNull();
            assertThat(scheduler.isShutdown()).isFalse();
        } finally {
            scheduler.shutdown();
        }
        assertThat(scheduler.isShutdown()).isTrue();
    }

    @Test
    void rejectsInvalidSchedulerSizeAndReconnectPolicy() {
        assertThatThrownBy(() -> configuration.zerodhaMarketDataScheduler(0))
                .isInstanceOf(IllegalArgumentException.class);

        ScheduledExecutorService scheduler = configuration.zerodhaMarketDataScheduler(1);
        try {
            assertThatThrownBy(() -> configuration.zerodhaLiveMarketDataProvider(
                    context -> { throw new UnsupportedOperationException(); }, scheduler,
                    new ObjectMapper(), 10, 500, 30, 0, 15, 2_097_152))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            scheduler.shutdown();
        }
    }
}
