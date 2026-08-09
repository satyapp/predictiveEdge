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
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcMarketIntelligenceStoreTest {
    @Test
    void appendsBarRevisionsAndRejectionsWithTenantIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var store = new JdbcMarketIntelligenceStore(jdbc);
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
    }
}
