package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.predictiveedge.broker.domain.IndexMarketTick;
import org.predictiveedge.broker.domain.EquityMarketTick;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.marketintelligence.application.MarketTickRejection;
import org.predictiveedge.marketintelligence.domain.BarFinalityState;
import org.predictiveedge.marketintelligence.domain.BarInterval;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.predictiveedge.marketintelligence.domain.ContentHash;
import org.predictiveedge.marketintelligence.domain.MarketBarKey;
import org.predictiveedge.marketintelligence.domain.MarketBarRevision;
import org.predictiveedge.marketintelligence.domain.MarketBarValues;
import org.predictiveedge.marketintelligence.domain.MarketSessionId;
import org.predictiveedge.marketintelligence.domain.ObservationSubject;
import org.predictiveedge.marketintelligence.domain.ObservationSubjectType;
import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.predictiveedge.platform.eventing.application.EventPublication;
import org.predictiveedge.platform.eventing.contract.DataClassification;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class JdbcMarketIntelligenceStoreTest {
    @Test
    void appendsBarRevisionsAndRejectionsWithTenantIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DomainEventPublisher events = mock(DomainEventPublisher.class);
        UUID eventId = UUID.randomUUID();
        var store = new JdbcMarketIntelligenceStore(jdbc, new ObjectMapper(), events, () -> eventId);
        UUID userId = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-07T03:45:00Z");
        var key = new MarketBarKey(new ObservationSubject(ObservationSubjectType.INSTRUMENT, "NSE:INFY"),
                new MarketSessionId("NSE", LocalDate.of(2026, 8, 7), "REGULAR"), BarTimeframe.ONE_MINUTE,
                new BarInterval(start, start.plusSeconds(60), false));
        var revision = new MarketBarRevision(key, 1,
                new MarketBarValues(new BigDecimal("100"), new BigDecimal("110"),
                        new BigDecimal("90"), new BigDecimal("105"), 12),
                start.plusSeconds(50), BarFinalityState.FINAL, start.plusSeconds(62), null,
                new ContentHash("a".repeat(64)), "tick-v1", "finality-v1");
        store.publish(userId, " ZD123 ", revision);

        var instrument = new Instrument("NSE", "NIFTY 50");
        var tick = new IndexMarketTick(instrument, "256265", new BigDecimal("25000"),
                new BigDecimal("24900"), new BigDecimal("25100"), new BigDecimal("24800"),
                new BigDecimal("24950"), new BigDecimal("0.2"), start, start.plusMillis(100));
        store.reject(new MarketTickRejection(userId, "ZD123", tick,
                MarketTickRejection.Reason.DUPLICATE, "already seen"));

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(anyString(), arguments.capture());
        assertThat(arguments.getAllValues().getFirst()).contains(userId, "ZD123", "NSE:INFY");
        assertThat(arguments.getAllValues().getLast()).contains(userId, "ZD123", "DUPLICATE");

        ArgumentCaptor<EventPublication> publication = ArgumentCaptor.forClass(EventPublication.class);
        verify(events).stage(publication.capture());
        var staged = publication.getValue();
        var metadata = staged.event().metadata();
        assertThat(staged.destinationTopic()).isEqualTo("pe.market-intelligence.v1");
        assertThat(staged.event().classification()).isEqualTo(DataClassification.CONFIDENTIAL);
        assertThat(metadata.eventId()).isEqualTo(eventId);
        assertThat(metadata.eventType()).isEqualTo("MarketIntelligence.MarketBarRevisionPublished");
        assertThat(metadata.aggregateType()).isEqualTo("MarketBar");
        assertThat(metadata.aggregateId()).matches("[0-9a-f]{64}");
        assertThat(metadata.partitionKey()).isEqualTo(metadata.aggregateId());
        assertThat(metadata.aggregateVersion()).isEqualTo(1);
        assertThat(metadata.effectiveAt()).isEqualTo(start.plusSeconds(60));
        assertThat(metadata.availableAt()).isEqualTo(start.plusSeconds(62));
        assertThat(metadata.accountId()).isEqualTo("ZD123");
        assertThat(metadata.evidenceManifestRef()).isEqualTo("a".repeat(64));
        var payload = staged.event().payload();
        assertThat(payload.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(payload.get("subjectId").asText()).isEqualTo("NSE:INFY");
        assertThat(payload.get("close").decimalValue()).isEqualByComparingTo("105");
        assertThat(payload.get("finalityState").asText()).isEqualTo("FINAL");
    }

    @Test
    void appendsAHashedFiveByFiveDepthSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new JdbcMarketIntelligenceStore(jdbc, new ObjectMapper(),
                mock(DomainEventPublisher.class), UUID::randomUUID);
        UUID userId = UUID.randomUUID();
        Instant exchangeAt = Instant.parse("2026-09-01T04:00:00Z");
        var levels = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(position -> new MarketDepthLevel(position,
                        new BigDecimal(101 - position), position * 10L, position)).toList();
        var asks = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(position -> new MarketDepthLevel(position,
                        new BigDecimal(100 + position), position * 20L, position)).toList();
        var tick = new EquityMarketTick(new Instrument("NSE", "INFY"), "408065", new BigDecimal("100"),
                1, new BigDecimal("99"), 10, 100, 100, new BigDecimal("98"), new BigDecimal("101"),
                new BigDecimal("97"), new BigDecimal("99"), levels, asks, exchangeAt, exchangeAt,
                exchangeAt.plusMillis(10));

        store.publish(userId, "ZD123", tick);

        ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), arguments.capture());
        assertThat(arguments.getValue()).contains(userId, "ZD123", "NSE", "INFY", "408065");
        assertThat(arguments.getValue()[10].toString()).matches("[0-9a-f]{64}");
    }
}
