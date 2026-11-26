package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.broker.domain.Instrument;

class IntradaySessionSummaryTest {
    private static final Instant CUTOFF = ist("2026-11-26T09:27:00");

    @Test
    void summarisesOnlyCompletedBarsOfTheCutoffSessionWithVwapLevelsVolumeAndDailyContext() {
        var candles = new ArrayList<Candle>();
        candles.add(bar("2026-11-25T09:15:00", "110", "90", "105", 1000));
        candles.add(bar("2026-11-25T09:20:00", "108", "101", "106", 800));
        candles.add(bar("2026-11-26T09:15:00", "102", "98", "100", 100));
        candles.add(bar("2026-11-26T09:20:00", "104", "99", "103", 300));
        candles.add(bar("2026-11-26T09:25:00", "106", "102", "104", 200));

        var summary = IntradaySessionSummary.build(json(), candles, CUTOFF, null);

        assertThat(summary.get("session_date").asText()).isEqualTo("2026-11-26");
        assertThat(summary.get("completed_bars").asInt()).isEqualTo(2);
        assertThat(summary.get("bars")).hasSize(2);
        assertThat(summary.get("vwap").asDouble()).isEqualTo(101.5);
        assertThat(summary.at("/opening_range/high").asDouble()).isEqualTo(104.0);
        assertThat(summary.at("/opening_range/low").asDouble()).isEqualTo(98.0);
        assertThat(summary.get("session_high").asDouble()).isEqualTo(104.0);
        assertThat(summary.get("session_low").asDouble()).isEqualTo(98.0);
        assertThat(summary.at("/volume/today").asLong()).isEqualTo(400L);
        assertThat(summary.at("/volume/average_same_time_prior_sessions").asDouble()).isEqualTo(1800.0);
        assertThat(summary.at("/daily_context/previous_close").asDouble()).isEqualTo(106.0);
        assertThat(summary.at("/daily_context/intraday_high").asDouble()).isEqualTo(104.0);
        assertThat(summary.at("/daily_context/intraday_low").asDouble()).isEqualTo(98.0);
        assertThat(summary.at("/daily_context/window_high").asDouble()).isEqualTo(110.0);
        assertThat(summary.at("/daily_context/window_low").asDouble()).isEqualTo(90.0);
        assertThat(summary.at("/daily_context/window_from").asText()).isEqualTo("2026-11-25");
    }

    @Test
    void reportsNoVwapWhenTheSessionHasNoCompletedBar() {
        var candles = List.of(bar("2026-11-25T09:15:00", "110", "90", "105", 1000));

        var summary = IntradaySessionSummary.build(json(), candles, CUTOFF, null);

        assertThat(summary.get("completed_bars").asInt()).isZero();
        assertThat(summary.get("vwap").isNull()).isTrue();
        assertThat(summary.at("/daily_context/previous_close").asDouble()).isEqualTo(105.0);
    }

    private static ObjectMapper json() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private static Instant ist(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(IntradaySessionSummary.MARKET_ZONE).toInstant();
    }

    private static Candle bar(String start, String high, String low, String close, long volume) {
        BigDecimal price = new BigDecimal(close);
        return new Candle(new Instrument("NSE", "INFY"), ist(start), price, new BigDecimal(high),
                new BigDecimal(low), price, volume, null);
    }
}
