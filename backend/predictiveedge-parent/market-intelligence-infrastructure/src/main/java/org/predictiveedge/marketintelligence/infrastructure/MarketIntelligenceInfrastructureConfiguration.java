package org.predictiveedge.marketintelligence.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.predictiveedge.broker.connection.UserMarketDataSubscriptionService;
import org.predictiveedge.marketintelligence.application.MarketBarPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketIntelligenceTickConsumer;
import org.predictiveedge.marketintelligence.application.MarketIntelligenceMetricsPort;
import org.predictiveedge.marketintelligence.application.MarketSessionPort;
import org.predictiveedge.marketintelligence.application.MarketSessionCalendarService;
import org.predictiveedge.marketintelligence.application.MarketSessionPublicationPort;
import org.predictiveedge.marketintelligence.application.MarketBarQueryPort;
import org.predictiveedge.marketintelligence.application.MarketBarQueryService;
import org.predictiveedge.marketintelligence.application.MarketTickRejectionPort;
import org.predictiveedge.marketintelligence.application.UserMarketIntelligenceSubscriptionService;
import org.predictiveedge.marketintelligence.domain.BarFinalityPolicy;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

@Configuration
public class MarketIntelligenceInfrastructureConfiguration {
    @Bean
    MarketIntelligenceMetricsPort marketIntelligenceMetrics(MeterRegistry registry) {
        return new MicrometerMarketIntelligenceMetrics(registry);
    }

    @Bean
    JdbcMarketIntelligenceStore marketIntelligenceStore(
            JdbcTemplate jdbc, ObjectMapper json, DomainEventPublisher events) {
        return new JdbcMarketIntelligenceStore(jdbc, json, events, UUID::randomUUID);
    }

    @Bean
    MarketSessionPort marketSessionPort(JdbcTemplate jdbc) {
        return new JdbcMarketSessionAdapter(jdbc);
    }

    @Bean
    MarketSessionPublicationPort marketSessionPublicationPort(JdbcTemplate jdbc) {
        return new JdbcMarketSessionPublicationAdapter(jdbc);
    }

    @Bean
    MarketSessionCalendarService marketSessionCalendarService(MarketSessionPublicationPort publications) {
        return new MarketSessionCalendarService(publications);
    }

    @Bean
    MarketBarQueryPort marketBarQueryPort(JdbcTemplate jdbc) {
        return new JdbcMarketBarQueryAdapter(jdbc);
    }

    @Bean
    JdbcMarketContextStore jdbcMarketContextStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcMarketContextStore(jdbc, json);
    }

    @Bean
    MarketBarQueryService marketBarQueryService(MarketBarQueryPort bars) {
        return new MarketBarQueryService(bars);
    }

    @Bean
    MarketIntelligenceTickConsumer marketIntelligenceTickConsumer(
            MarketSessionPort sessions,
            MarketBarPublicationPort publications,
            MarketTickRejectionPort rejections,
            MarketIntelligenceMetricsPort metrics,
            @Value("${predictiveedge.market-intelligence.timeframes:ONE_MINUTE,FIVE_MINUTES}") String timeframes,
            @Value("${predictiveedge.market-intelligence.allowed-lateness:PT2S}") Duration allowedLateness,
            @Value("${predictiveedge.market-intelligence.aggregation-policy-version:tick-ohlcv-v1}") String aggregationVersion,
            @Value("${predictiveedge.market-intelligence.finality-policy-version:finality-v1}") String finalityVersion) {
        return new MarketIntelligenceTickConsumer(sessions, publications, rejections, metrics,
                parseTimeframes(timeframes), new BarFinalityPolicy(allowedLateness, finalityVersion),
                aggregationVersion);
    }

    @Bean
    UserMarketIntelligenceSubscriptionService userMarketIntelligenceSubscriptionService(
            UserMarketDataSubscriptionService subscriptions, MarketIntelligenceTickConsumer consumer) {
        return new UserMarketIntelligenceSubscriptionService(subscriptions, consumer);
    }

    @Bean
    MarketIntelligenceRetentionReaper marketIntelligenceRetentionReaper(
            MarketIntelligenceTickConsumer consumer,
            @Value("${predictiveedge.market-intelligence.session-retention:PT24H}") Duration retention) {
        return new MarketIntelligenceRetentionReaper(consumer, Clock.systemUTC(), retention);
    }

    static Set<BarTimeframe> parseTimeframes(String configured) {
        if (configured == null || configured.isBlank())
            throw new IllegalArgumentException("At least one market-intelligence timeframe is required");
        EnumSet<BarTimeframe> parsed = EnumSet.noneOf(BarTimeframe.class);
        Arrays.stream(configured.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> BarTimeframe.valueOf(value.toUpperCase(Locale.ROOT)))
                .forEach(parsed::add);
        if (parsed.isEmpty()) throw new IllegalArgumentException("At least one market-intelligence timeframe is required");
        return parsed;
    }
}
