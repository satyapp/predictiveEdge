package org.predictiveedge.decision.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.connection.BrokerAccountEvidence;
import org.predictiveedge.broker.connection.BrokerAccountEvidencePort;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;
import org.predictiveedge.broker.domain.BrokerFundsSegment;
import org.predictiveedge.broker.domain.BrokerId;
import org.predictiveedge.broker.domain.BrokerPosition;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.marketintelligence.application.MarketDepthQueryPort;
import org.predictiveedge.marketintelligence.application.MarketDepthSnapshot;

class SafetySnapshotProducerTest {
    private static final Instant NOW = Instant.parse("2026-09-01T04:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final Instrument MARKET = new Instrument("NSE", "INFY");
    private static final InstrumentRef DECISION = new InstrumentRef("NSE", "INE009A01021");
    private final List<ExecutionEvidenceSnapshot> executions = new ArrayList<>();
    private final List<RiskSnapshot> risks = new ArrayList<>();
    private final List<PortfolioSnapshot> portfolios = new ArrayList<>();

    @Test
    void producesPassingBuySafetyFactsFromVisibleDepthAndAFlatBrokerAccount() {
        var producer = producer(Optional.of(depth()), Optional.of(account(List.of())));

        var result = producer.assess(request(RecommendationAction.BUY, "100"));

        assertThat(result.execution().gateDisposition()).isEqualTo(GateDisposition.PASS);
        assertThat(result.execution().context().direction()).isEqualTo(RecommendationAction.BUY);
        assertThat(result.execution().context().visibleEntryQuantity()).isEqualTo(500);
        assertThat(result.execution().context().estimatedFillPrice()).isEqualByComparingTo("101.33333333");
        assertThat(result.risk().gateDisposition()).isEqualTo(GateDisposition.PASS);
        assertThat(result.risk().proposedTradeRisk()).isEqualByComparingTo("199.99999950");
        assertThat(result.portfolio().gateDisposition()).isEqualTo(GateDisposition.PASS);
        assertThat(result.portfolio().projectedInstrumentWeightPercent()).isGreaterThan(BigDecimal.ZERO);
        assertThat(executions).containsExactly(result.execution());
        assertThat(risks).containsExactly(result.risk());
        assertThat(portfolios).containsExactly(result.portfolio());
    }

    @Test
    void consumesBidsForSellAndRejectsAStopOnTheWrongSide() {
        var producer = producer(Optional.of(depth()), Optional.of(account(List.of())));

        var result = producer.assess(request(RecommendationAction.SELL, "99"));

        assertThat(result.execution().context().direction()).isEqualTo(RecommendationAction.SELL);
        assertThat(result.execution().context().estimatedFillPrice()).isEqualByComparingTo("99.66666667");
        assertThat(result.risk().readiness()).isEqualTo(AssessmentReadiness.INVALID);
        assertThat(result.risk().gateDisposition()).isEqualTo(GateDisposition.VETO);
    }

    @Test
    void missingEvidenceCreatesThreePersistedUnavailableVetoes() {
        var result = producer(Optional.empty(), Optional.empty())
                .assess(request(RecommendationAction.BUY, "100"));

        assertThat(result.execution().readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
        assertThat(result.execution().context()).isNull();
        assertThat(result.execution().gateDisposition()).isEqualTo(GateDisposition.VETO);
        assertThat(result.risk().readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
        assertThat(result.risk().gateDisposition()).isEqualTo(GateDisposition.VETO);
        assertThat(result.portfolio().readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
        assertThat(result.portfolio().gateDisposition()).isEqualTo(GateDisposition.VETO);
        assertThat(executions).hasSize(1);
        assertThat(risks).hasSize(1);
        assertThat(portfolios).hasSize(1);
    }

    @Test
    void openPositionsWithoutStopEvidenceMakeRiskUnavailableAndVetoed() {
        var position = new BrokerPosition(MARKET, "408065", "MIS", 10, 0,
                money("100"), money("99"), money("101"), money("1010"), money("10"),
                money("10"), money("10"), BigDecimal.ZERO, 10, 0);

        var result = producer(Optional.of(depth()), Optional.of(account(List.of(position))))
                .assess(request(RecommendationAction.BUY, "100"));

        assertThat(result.risk().readiness()).isEqualTo(AssessmentReadiness.UNAVAILABLE);
        assertThat(result.risk().gateDisposition()).isEqualTo(GateDisposition.VETO);
        assertThat(result.risk().currentOpenRisk()).isZero();
        assertThat(result.risk().openRiskComplete()).isFalse();
        assertThat(result.risk().unassessedOpenPositionCount()).isEqualTo(1);
    }

    private SafetySnapshotProducer producer(Optional<MarketDepthSnapshot> depth,
            Optional<BrokerAccountEvidence> account) {
        MarketDepthQueryPort depths = (user, accountId, instrument, cutoff) -> depth;
        BrokerAccountEvidencePort accounts = new BrokerAccountEvidencePort() {
            @Override public BrokerAccountEvidence publish(UUID userId, BrokerAccountSnapshot snapshot) {
                throw new UnsupportedOperationException();
            }
            @Override public Optional<BrokerAccountEvidence> latestAtOrBefore(
                    UUID userId, String accountId, Instant cutoff) { return account; }
        };
        var ids = new AtomicInteger();
        return new SafetySnapshotProducer(depths, accounts,
                value -> { executions.add(value); return true; },
                value -> { risks.add(value); return true; },
                value -> { portfolios.add(value); return true; },
                new ExecutionAssessmentPolicy("execution-v1", Duration.ofSeconds(3), Duration.ofSeconds(2),
                        money("200"), money("100"), money("10"), 50),
                new PersonalRiskPolicy("risk-v1", money("1000"), money("2000"), money("5000"),
                        money("25000"), Duration.ofSeconds(5), Duration.ofSeconds(2)),
                new PersonalPortfolioPolicy("portfolio-v1", money("25"), false,
                        Duration.ofSeconds(5), Duration.ofSeconds(2)),
                Clock.fixed(NOW, ZoneOffset.UTC), () -> Integer.toString(ids.incrementAndGet()));
    }

    private static SafetyAssessmentRequest request(RecommendationAction direction, String stop) {
        return new SafetyAssessmentRequest(USER, "ZD123", DECISION, MARKET, direction, 150, money(stop),
                NOW.minusSeconds(2), NOW.minusSeconds(1));
    }

    private static MarketDepthSnapshot depth() {
        return new MarketDepthSnapshot(UUID.randomUUID(), USER, "ZD123", MARKET, "408065",
                levels("100", -1, 100), levels("101", 1, 100), NOW.minusSeconds(1),
                NOW.minusSeconds(1), "d".repeat(64));
    }

    private static List<MarketDepthLevel> levels(String start, int direction, long quantity) {
        BigDecimal first = money(start);
        return java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(position -> new MarketDepthLevel(position,
                        first.add(BigDecimal.valueOf((long) direction * (position - 1))), quantity, position))
                .toList();
    }

    private static BrokerAccountEvidence account(List<BrokerPosition> positions) {
        var funds = new BrokerFundsSegment("equity", true, money("100000"),
                Map.of("live_balance", money("100000")), Map.of("debits", BigDecimal.ZERO));
        var snapshot = new BrokerAccountSnapshot(new BrokerId("zerodha"), "ZD123", Map.of("equity", funds),
                positions, positions, List.of(), NOW.minusSeconds(1), NOW.minusSeconds(1));
        return new BrokerAccountEvidence(UUID.randomUUID(), snapshot, "a".repeat(64));
    }

    private static BigDecimal money(String value) { return new BigDecimal(value); }
}
