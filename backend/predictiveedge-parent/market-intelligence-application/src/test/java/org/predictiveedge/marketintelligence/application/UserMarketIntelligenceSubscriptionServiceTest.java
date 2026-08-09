package org.predictiveedge.marketintelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionService;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionStatus;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.spi.BrokerContext;

class UserMarketIntelligenceSubscriptionServiceTest {
    @Test
    void alwaysAttachesTheCanonicalConsumerToTheUsersResolvedStream() {
        var subscriptions = mock(UserMarketDataSubscriptionService.class);
        var consumer = mock(MarketIntelligenceTickConsumer.class);
        var context = BrokerContext.withoutCredentials(UUID.randomUUID(), "ZD123");
        var instruments = List.of(new Instrument("NSE", "INFY"));
        var expected = mock(UserMarketDataSubscriptionStatus.class);
        when(subscriptions.subscribe(context, instruments, consumer)).thenReturn(expected);

        var service = new UserMarketIntelligenceSubscriptionService(subscriptions, consumer);

        assertThat(service.subscribe(context, instruments)).isSameAs(expected);
        verify(subscriptions).subscribe(context, instruments, consumer);
    }
}
