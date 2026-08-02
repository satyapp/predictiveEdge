package org.predictiveedge.guardian.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TradeMonitoringCaseTest {
    private static final Instant ENTRY_TIME = Instant.parse("2026-08-01T09:20:00Z");
    private static final Instant REGISTERED_AT = Instant.parse("2026-08-01T09:21:00Z");

    @Test
    void registersActualFillAsActiveMonitoringCase() {
        TradeMonitoringCase monitoringCase = newCase();

        assertThat(monitoringCase.state()).isEqualTo(MonitoringState.ACTIVE);
        assertThat(monitoringCase.version()).isEqualTo(1);
        assertThat(monitoringCase.entryFill().quantity()).isEqualByComparingTo("10");
        assertThat(monitoringCase.exitFill()).isNull();
    }

    @Test
    void suspendsAndResumesWithoutChangingOriginalTradeEvidence() {
        TradeMonitoringCase original = newCase();

        TradeMonitoringCase suspended = original.suspend("Market data stale", REGISTERED_AT.plusSeconds(30));
        TradeMonitoringCase resumed = suspended.resume(REGISTERED_AT.plusSeconds(60));

        assertThat(suspended.state()).isEqualTo(MonitoringState.SUSPENDED);
        assertThat(suspended.suspensionReason()).isEqualTo("Market data stale");
        assertThat(resumed.state()).isEqualTo(MonitoringState.ACTIVE);
        assertThat(resumed.version()).isEqualTo(3);
        assertThat(resumed.entryFill()).isEqualTo(original.entryFill());
        assertThat(resumed.approvedTradePlanRef()).isEqualTo(original.approvedTradePlanRef());
    }

    @Test
    void completesFromSuspendedStateUsingActualFullExit() {
        TradeMonitoringCase suspended = newCase().suspend("User paused monitoring", REGISTERED_AT.plusSeconds(10));
        ManualFill exit = new ManualFill(new BigDecimal("10.0"), new BigDecimal("112.50"),
                REGISTERED_AT.plusSeconds(30), "broker-exit-1");

        TradeMonitoringCase completed = suspended.complete(exit, REGISTERED_AT.plusSeconds(40));

        assertThat(completed.state()).isEqualTo(MonitoringState.COMPLETED);
        assertThat(completed.exitFill()).isEqualTo(exit);
        assertThat(completed.suspensionReason()).isNull();
        assertThat(completed.version()).isEqualTo(3);
    }

    @Test
    void rejectsRegistrationBeforeTheActualEntryFill() {
        ManualFill entry = entryFill();

        assertThatIllegalArgumentException().isThrownBy(() -> TradeMonitoringCase.register(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "rec-1", "plan-1", "account-1",
                new InstrumentRef("nse", "infy"), TradeDirection.LONG, entry, ENTRY_TIME.minusSeconds(1)))
                .withMessageContaining("Registration cannot precede");
    }

    @Test
    void rejectsPartialExitUntilAdjustmentLifecycleIsDesigned() {
        ManualFill partialExit = new ManualFill(new BigDecimal("5"), new BigDecimal("112"),
                REGISTERED_AT.plusSeconds(20), null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> newCase().complete(partialExit, REGISTERED_AT.plusSeconds(30)))
                .withMessageContaining("full-position exits only");
    }

    @Test
    void rejectsInvalidAndOutOfOrderTransitions() {
        TradeMonitoringCase original = newCase();

        assertThatIllegalStateException().isThrownBy(() -> original.resume(REGISTERED_AT.plusSeconds(1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> original.suspend("data stale", REGISTERED_AT.minusSeconds(1)))
                .withMessageContaining("chronological");
    }

    private static TradeMonitoringCase newCase() {
        return TradeMonitoringCase.register(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "rec-1", "plan-v1", "account-1", new InstrumentRef("nse", "infy"),
                TradeDirection.LONG, entryFill(), REGISTERED_AT);
    }

    private static ManualFill entryFill() {
        return new ManualFill(new BigDecimal("10"), new BigDecimal("100.25"), ENTRY_TIME, "broker-entry-1");
    }
}
