package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;

class DecisionSafetyResourceQueriesTest {
    private static final Instant NOW = Instant.parse("2026-08-15T05:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final InstrumentRef EQUITY = new InstrumentRef("NSE", "INE002A01018");
    private static final ShadowScope SCOPE = new ShadowScope(USER, EQUITY);
    private static final String HASH = "d".repeat(64);

    @Test
    void preservesExplicitRiskAndPortfolioPassGates() {
        var risk = new RiskDecisionResourceQuery((scope, cutoff) -> Optional.of(risk()));
        var portfolio = new PortfolioDecisionResourceQuery((scope, cutoff) -> Optional.of(portfolio()));

        assertThat(risk.findLatest(SCOPE, NOW).gateDisposition()).isEqualTo(GateDisposition.PASS);
        assertThat(portfolio.findLatest(SCOPE, NOW).gateDisposition()).isEqualTo(GateDisposition.PASS);
    }

    @Test
    void derivesExecutionVetoFromInfeasibleEntryOrExit() {
        var execution = new ExecutionDecisionResourceQuery(
                (scope, cutoff) -> Optional.of(execution(false, true)));

        assertThat(execution.findLatest(SCOPE, NOW).gateDisposition()).isEqualTo(GateDisposition.VETO);
    }

    @Test
    void missingMandatorySafetyEvidenceIsUnavailableAndVetoed() {
        var risk = new RiskDecisionResourceQuery((scope, cutoff) -> Optional.empty());
        var portfolio = new PortfolioDecisionResourceQuery((scope, cutoff) -> Optional.empty());
        var execution = new ExecutionDecisionResourceQuery((scope, cutoff) -> Optional.empty());

        assertThat(List.of(risk.findLatest(SCOPE, NOW), portfolio.findLatest(SCOPE, NOW),
                execution.findLatest(SCOPE, NOW))).allSatisfy(resource -> {
                    assertThat(resource.readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
                    assertThat(resource.gateDisposition()).isEqualTo(GateDisposition.VETO);
                    assertThat(resource.isUsableAt(NOW)).isFalse();
                });
    }

    private static RiskSnapshot risk() {
        return new RiskSnapshot("risk-1", USER, EQUITY, AssessmentReadiness.READY, GateDisposition.PASS,
                money(100000), money(1000), money(2000), money(500), money(5000), money(25000), true,
                "risk-v1", NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1), NOW.plusSeconds(30),
                List.of("broker-account:1"), HASH);
    }

    private static PortfolioSnapshot portfolio() {
        return new PortfolioSnapshot("portfolio-1", USER, EQUITY, AssessmentReadiness.READY, GateDisposition.PASS,
                money(80000), money(20000), money(20000), BigDecimal.TEN, money(20000), money(20), money(20),
                money(25), 1, "portfolio-v1", NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1),
                NOW.plusSeconds(30), List.of("broker-account:1"), HASH);
    }

    private static ExecutionEvidenceSnapshot execution(boolean entry, boolean exit) {
        ExecutionContext context = new ExecutionContext(NOW.minusSeconds(1), money(99), money(101), "depth:1",
                1, money(101), BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 100,
                entry, exit, NOW.plusSeconds(10));
        return new ExecutionEvidenceSnapshot("execution-1", USER, EQUITY, AssessmentReadiness.READY, context,
                NOW.minusSeconds(3), NOW.minusSeconds(2), NOW.minusSeconds(1), List.of("depth:1"), HASH);
    }

    private static BigDecimal money(long value) { return BigDecimal.valueOf(value); }
}
