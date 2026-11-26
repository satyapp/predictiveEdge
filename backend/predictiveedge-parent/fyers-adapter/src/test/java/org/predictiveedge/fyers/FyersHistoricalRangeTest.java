package org.predictiveedge.fyers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class FyersHistoricalRangeTest {
    @Test
    void endsTheRangeAtThePreviousWholeMinuteSoThePartialCandleIsLeftOut() {
        assertThat(FyersHistoricalDataProvider.completedMinute(Instant.parse("2026-11-26T03:39:30Z")))
                .isEqualTo(Instant.parse("2026-11-26T03:38:00Z"));
        assertThat(FyersHistoricalDataProvider.completedMinute(Instant.parse("2026-11-26T03:39:00Z")))
                .isEqualTo(Instant.parse("2026-11-26T03:38:00Z"));
    }
}
