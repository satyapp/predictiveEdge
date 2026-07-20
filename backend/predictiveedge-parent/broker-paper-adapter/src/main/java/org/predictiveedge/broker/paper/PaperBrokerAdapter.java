package org.predictiveedge.broker.paper;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.predictiveedge.broker.domain.BrokerAccount;
import org.predictiveedge.broker.domain.BrokerCapability;
import org.predictiveedge.broker.domain.BrokerFailure;
import org.predictiveedge.broker.domain.BrokerId;
import org.predictiveedge.broker.domain.BrokerOrder;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.OrderRequest;
import org.predictiveedge.broker.domain.OrderSide;
import org.predictiveedge.broker.domain.OrderStatus;
import org.predictiveedge.broker.domain.OrderType;
import org.predictiveedge.broker.spi.BrokerAdapter;
import org.predictiveedge.broker.spi.BrokerContext;

public final class PaperBrokerAdapter implements BrokerAdapter {
    private static final BrokerId ID = new BrokerId("paper");
    private static final Set<BrokerCapability> CAPABILITIES = Set.of(
            BrokerCapability.ACCOUNT_SNAPSHOT,
            BrokerCapability.MARKET_ORDER,
            BrokerCapability.ORDER_LOOKUP);

    private final BigDecimal startingCash;
    private final PaperQuoteProvider quotes;
    private final Clock clock;
    private final Map<AccountKey, AccountState> accounts = new ConcurrentHashMap<>();

    public PaperBrokerAdapter(BigDecimal startingCash, PaperQuoteProvider quotes, Clock clock) {
        if (startingCash == null || startingCash.signum() < 0) {
            throw new IllegalArgumentException("Starting cash cannot be negative");
        }
        this.startingCash = startingCash;
        this.quotes = java.util.Objects.requireNonNull(quotes, "Quote provider is required");
        this.clock = java.util.Objects.requireNonNull(clock, "Clock is required");
    }

    @Override public BrokerId id() { return ID; }
    @Override public String displayName() { return "Paper Trading"; }
    @Override public Set<BrokerCapability> capabilities() { return CAPABILITIES; }

    @Override
    public BrokerAccount account(BrokerContext context) {
        AccountState state = state(context);
        synchronized (state) {
            return state.snapshot(context.brokerAccountId());
        }
    }

    @Override
    public BrokerOrder placeOrder(BrokerContext context, OrderRequest request) {
        if (request.type() != OrderType.MARKET) {
            throw new BrokerFailure(BrokerFailure.Code.UNSUPPORTED_ORDER_TYPE,
                    "Paper Trading currently supports market orders only");
        }
        AccountState state = state(context);
        synchronized (state) {
            BrokerOrder duplicate = state.orders.get(request.clientOrderId());
            if (duplicate != null) {
                return duplicate;
            }
            BigDecimal price = quotes.marketPrice(request.instrument());
            if (price == null || price.signum() <= 0) {
                throw new BrokerFailure(BrokerFailure.Code.CONNECTION_UNAVAILABLE,
                        "No executable market price is available");
            }
            BigDecimal value = price.multiply(request.quantity());
            BigDecimal held = state.positions.getOrDefault(request.instrument(), BigDecimal.ZERO);
            if (request.side() == OrderSide.BUY && state.cash.compareTo(value) < 0) {
                throw new BrokerFailure(BrokerFailure.Code.INSUFFICIENT_FUNDS, "Insufficient paper cash");
            }
            if (request.side() == OrderSide.SELL && held.compareTo(request.quantity()) < 0) {
                throw new BrokerFailure(BrokerFailure.Code.INSUFFICIENT_POSITION, "Insufficient paper position");
            }

            BigDecimal positionChange = request.side() == OrderSide.BUY
                    ? request.quantity() : request.quantity().negate();
            BigDecimal cashChange = request.side() == OrderSide.BUY ? value.negate() : value;
            state.cash = state.cash.add(cashChange);
            BigDecimal newPosition = held.add(positionChange);
            if (newPosition.signum() == 0) {
                state.positions.remove(request.instrument());
            } else {
                state.positions.put(request.instrument(), newPosition);
            }

            BrokerOrder order = new BrokerOrder(
                    "paper-" + request.clientOrderId(), request.clientOrderId(), request.instrument(),
                    request.side(), request.type(), OrderStatus.FILLED, request.quantity(), request.quantity(),
                    price, clock.instant());
            state.orders.put(request.clientOrderId(), order);
            return order;
        }
    }

    @Override
    public Optional<BrokerOrder> findOrder(BrokerContext context, UUID clientOrderId) {
        AccountState state = state(context);
        synchronized (state) {
            return Optional.ofNullable(state.orders.get(clientOrderId));
        }
    }

    private AccountState state(BrokerContext context) {
        return accounts.computeIfAbsent(
                new AccountKey(context.userId(), context.brokerAccountId()), ignored -> new AccountState());
    }

    private record AccountKey(UUID userId, String accountId) {}

    private final class AccountState {
        private BigDecimal cash = startingCash;
        private final Map<Instrument, BigDecimal> positions = new HashMap<>();
        private final Map<UUID, BrokerOrder> orders = new LinkedHashMap<>();

        private BrokerAccount snapshot(String accountId) {
            return new BrokerAccount(ID, accountId, "Paper Trading", cash, positions, clock.instant());
        }
    }
}
