package org.predictiveedge.platform.eventing;

import java.util.List;

/**
 * Versioned Kafka topic names shared by the Platform Core bootstrap.
 *
 * <p>Topic payload schemas belong in the event-contract modules that will own
 * each business event. Keeping names here prevents bootstrap configuration
 * from silently diverging from the architecture decision.</p>
 */
public final class PlatformKafkaTopics {
    public static final String MARKET_DATA_TRADES = "pe.market-data.trades.v1";
    public static final String MARKET_DATA_QUOTES = "pe.market-data.quotes.v1";
    public static final String MARKET_DATA_BARS = "pe.market-data.bars.v1";
    public static final String MARKET_CONTEXT = "pe.market-context.v1";
    public static final String CHART_CONTEXT = "pe.chart-context.v1";
    public static final String DECISIONS = "pe.decisions.v1";
    public static final String ORDERS = "pe.orders.v1";
    public static final String POSITIONS = "pe.positions.v1";
    public static final String RISK = "pe.risk.v1";
    public static final String SESSION_EVENTS = "pe.session-events.v1";
    public static final String TRADE_GUARDIAN = "pe.trade-guardian.v1";

    public static final List<String> ALL = List.of(
            MARKET_DATA_TRADES,
            MARKET_DATA_QUOTES,
            MARKET_DATA_BARS,
            MARKET_CONTEXT,
            CHART_CONTEXT,
            DECISIONS,
            ORDERS,
            POSITIONS,
            RISK,
            SESSION_EVENTS,
            TRADE_GUARDIAN);

    private PlatformKafkaTopics() {
    }
}
