package org.predictiveedge.decision.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionSafetySnapshotsTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final String HASH = "a".repeat(64);

    @Test
    void riskMustExplicitlyPassOrVetoAndMatchTradingPermission() {
        assertThatThrownBy(() -> risk(GateDisposition.NOT_APPLICABLE, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pass or veto");
        assertThatThrownBy(() -> risk(GateDisposition.PASS, false))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trading-allowed");
    }

    @Test
    void portfolioRejectsImpossibleConcentrationPercentages() {
        assertThatThrownBy(() -> new PortfolioSnapshot("portfolio-1", USER, EQUITY,
                AssessmentReadiness.READY, GateDisposition.PASS, money(100000), money(20000), money(20000),
                BigDecimal.TEN, money(20000), money(101), money(20), money(25), 1, "portfolio-v1",
                NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1), NOW.plusSeconds(30),
                List.of("broker-account:1"), HASH)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100 percent");
    }

    @Test
    void passGatesCannotContradictKnownRiskOrConcentrationBreaches() {
        assertThatThrownBy(() -> new RiskSnapshot("risk-2", USER, EQUITY, AssessmentReadiness.READY,
                GateDisposition.PASS, money(100000), money(1000), BigDecimal.ZERO, money(500), money(5000),
                money(25000), true, "risk-v1", NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1),
                NOW.plusSeconds(30), List.of("broker-account:1"), HASH)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("risk capacity");
        assertThatThrownBy(() -> new PortfolioSnapshot("portfolio-2", USER, EQUITY,
                AssessmentReadiness.READY, GateDisposition.PASS, money(100000), money(30000), money(30000),
                BigDecimal.TEN, money(30000), money(30), money(30), money(25), 1, "portfolio-v1",
                NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1), NOW.plusSeconds(30),
                List.of("broker-account:1"), HASH)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("concentration");
    }

    @Test
    void executionEvidenceCannotClaimAvailabilityBeforeItsQuote() {
        ExecutionContext context = new ExecutionContext(NOW, money(99), money(101), "depth:1", 1,
                money(101), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 100,
                true, true, NOW.plusSeconds(10));
        assertThatThrownBy(() -> new ExecutionEvidenceSnapshot("execution-1", USER, EQUITY,
                AssessmentReadiness.READY, context, NOW.minusSeconds(3), NOW.minusSeconds(2),
                NOW.minusSeconds(1), List.of("depth:1"), HASH)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("causal times");
    }

    private static RiskSnapshot risk(GateDisposition gate, boolean allowed) {
        return new RiskSnapshot("risk-1", USER, EQUITY, AssessmentReadiness.READY, gate, money(100000),
                money(1000), money(2000), money(500), money(5000), money(25000), allowed, "risk-v1",
                NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1), NOW.plusSeconds(30),
                List.of("broker-account:1"), HASH);
    }

    private static BigDecimal money(long value) { return BigDecimal.valueOf(value); }
}
