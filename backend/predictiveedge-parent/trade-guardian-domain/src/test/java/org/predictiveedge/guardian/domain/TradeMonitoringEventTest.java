package org.predictiveedge.guardian.domain;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TradeMonitoringEventTest {
    @Test
    void rejectsEventTypeThatDoesNotMatchTheLifecycleSnapshot() {
        Instant now = Instant.parse("2026-08-02T05:00:00Z");
        TradeMonitoringCase active = TradeMonitoringCase.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "rec-1", "plan-1", "account-1",
                new InstrumentRef("NSE", "INFY"), TradeDirection.LONG,
                new ManualFill(new BigDecimal("2"), new BigDecimal("100"), now, null), now);

        assertThatIllegalArgumentException().isThrownBy(() -> new TradeMonitoringEvent(
                TradeMonitoringEvent.Type.MONITORING_COMPLETED, active));
    }
}
