package org.predictiveedge.broker.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.OrderRequest;
import org.predictiveedge.broker.domain.OrderSide;
import org.predictiveedge.broker.domain.OrderStatus;
import org.predictiveedge.broker.domain.OrderType;
import org.predictiveedge.broker.spi.BrokerContext;

class PaperBrokerAdapterTest {
    private static final Instrument RELIANCE = new Instrument("NSE", "RELIANCE");
    private PaperBrokerAdapter adapter;
    private BrokerContext context;

    @BeforeEach
    void setUp() {
        adapter = new PaperBrokerAdapter(
                new BigDecimal("100000.00"),
                instrument -> new BigDecimal("2500.00"),
                Clock.fixed(Instant.parse("2026-07-16T10:00:00Z"), ZoneOffset.UTC));
        context = BrokerContext.withoutCredentials(UUID.randomUUID(), "paper-main");
    }

    @Test
    void marketBuyFillsImmediatelyAndUpdatesTheAccount() {
        UUID clientOrderId = UUID.randomUUID();
        var request = new OrderRequest(clientOrderId, RELIANCE, OrderSide.BUY, OrderType.MARKET,
                new BigDecimal("10"), null);

        var order = adapter.placeOrder(context, request);
        var account = adapter.account(context);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.averageFillPrice()).isEqualByComparingTo("2500.00");
        assertThat(account.availableCash()).isEqualByComparingTo("75000.00");
        assertThat(account.positions()).containsEntry(RELIANCE, new BigDecimal("10"));
    }

    @Test
    void clientOrderIdMakesPlacementIdempotent() {
        UUID clientOrderId = UUID.randomUUID();
        var request = new OrderRequest(clientOrderId, RELIANCE, OrderSide.BUY, OrderType.MARKET,
                BigDecimal.ONE, null);

        var first = adapter.placeOrder(context, request);
        var repeated = adapter.placeOrder(context, request);

        assertThat(repeated).isEqualTo(first);
        assertThat(adapter.account(context).availableCash()).isEqualByComparingTo("97500.00");
    }

    @Test
    void rejectsOrdersThatWouldCreateAnUnfundedPosition() {
        var request = new OrderRequest(UUID.randomUUID(), RELIANCE, OrderSide.SELL, OrderType.MARKET,
                BigDecimal.ONE, null);

        assertThatThrownBy(() -> adapter.placeOrder(context, request))
                .isInstanceOf(BrokerFailure.class)
                .extracting(failure -> ((BrokerFailure) failure).code())
                .isEqualTo(BrokerFailure.Code.INSUFFICIENT_POSITION);
    }

    @Test
    void separateUsersHaveSeparatePaperAccounts() {
        var request = new OrderRequest(UUID.randomUUID(), RELIANCE, OrderSide.BUY, OrderType.MARKET,
                BigDecimal.ONE, null);
        adapter.placeOrder(context, request);
        var anotherUser = BrokerContext.withoutCredentials(UUID.randomUUID(), "paper-main");

        assertThat(adapter.account(anotherUser).availableCash()).isEqualByComparingTo("100000.00");
        assertThat(adapter.account(anotherUser).positions()).isEmpty();
    }
}
