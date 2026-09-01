package org.predictiveedge.decision.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.predictiveedge.broker.connection.BrokerAccountEvidence;
import org.predictiveedge.broker.connection.BrokerAccountEvidencePort;
import org.predictiveedge.broker.domain.BrokerAccountSnapshot;
import org.predictiveedge.broker.domain.BrokerHolding;
import org.predictiveedge.broker.domain.BrokerPosition;
import org.predictiveedge.broker.domain.MarketDepthLevel;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.ExecutionContext;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.RecommendationAction;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.marketintelligence.application.MarketDepthQueryPort;
import org.predictiveedge.marketintelligence.application.MarketDepthSnapshot;

/** Produces the three mandatory safety facts. It cannot create direction or place an order. */
public final class SafetySnapshotProducer {
    private static final BigDecimal BPS = BigDecimal.valueOf(10_000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final MarketDepthQueryPort depths;
    private final BrokerAccountEvidencePort accounts;
    private final ExecutionEvidencePublicationPort executionPublications;
    private final RiskSnapshotPublicationPort riskPublications;
    private final PortfolioSnapshotPublicationPort portfolioPublications;
    private final ExecutionAssessmentPolicy executionPolicy;
    private final PersonalRiskPolicy riskPolicy;
    private final PersonalPortfolioPolicy portfolioPolicy;
    private final Clock clock;
    private final Supplier<String> ids;

    public SafetySnapshotProducer(MarketDepthQueryPort depths, BrokerAccountEvidencePort accounts,
            ExecutionEvidencePublicationPort executionPublications,
            RiskSnapshotPublicationPort riskPublications,
            PortfolioSnapshotPublicationPort portfolioPublications,
            ExecutionAssessmentPolicy executionPolicy, PersonalRiskPolicy riskPolicy,
            PersonalPortfolioPolicy portfolioPolicy, Clock clock, Supplier<String> ids) {
        this.depths = Objects.requireNonNull(depths, "Market depth query is required");
        this.accounts = Objects.requireNonNull(accounts, "Broker account evidence query is required");
        this.executionPublications = Objects.requireNonNull(executionPublications, "Execution publication is required");
        this.riskPublications = Objects.requireNonNull(riskPublications, "Risk publication is required");
        this.portfolioPublications = Objects.requireNonNull(portfolioPublications, "Portfolio publication is required");
        this.executionPolicy = Objects.requireNonNull(executionPolicy, "Execution policy is required");
        this.riskPolicy = Objects.requireNonNull(riskPolicy, "Risk policy is required");
        this.portfolioPolicy = Objects.requireNonNull(portfolioPolicy, "Portfolio policy is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.ids = Objects.requireNonNull(ids, "Snapshot id supplier is required");
    }

    public SafetyAssessmentResult assess(SafetyAssessmentRequest request) {
        Objects.requireNonNull(request, "Safety assessment request is required");
        Instant availableAt = clock.instant();
        if (availableAt.isBefore(request.knowledgeCutoff()))
            throw new IllegalArgumentException("Knowledge cutoff cannot be in the future");
        Optional<MarketDepthSnapshot> depth = depths.latestAtOrBefore(request.userId(),
                request.brokerAccountId(), request.marketInstrument(), request.knowledgeCutoff());
        Optional<BrokerAccountEvidence> account = accounts.latestAtOrBefore(request.userId(),
                request.brokerAccountId(), request.knowledgeCutoff());
        ExecutionEvidenceSnapshot execution = execution(request, depth, availableAt);
        RiskSnapshot risk = risk(request, account, execution, availableAt);
        PortfolioSnapshot portfolio = portfolio(request, account, execution, availableAt);
        executionPublications.append(execution);
        riskPublications.append(risk);
        portfolioPublications.append(portfolio);
        return new SafetyAssessmentResult(execution, risk, portfolio);
    }

    private ExecutionEvidenceSnapshot execution(SafetyAssessmentRequest request,
            Optional<MarketDepthSnapshot> selected, Instant availableAt) {
        if (selected.isEmpty()) return unavailableExecution(request, availableAt, "missing-depth");
        MarketDepthSnapshot depth = selected.orElseThrow();
        Instant depthExpiry = depth.receivedAt().plus(executionPolicy.maximumDepthAge());
        if (!availableAt.isBefore(depthExpiry)) return unavailableExecution(request, availableAt, "stale-depth");
        BigDecimal bid = depth.buyDepth().getFirst().price();
        BigDecimal ask = depth.sellDepth().getFirst().price();
        if (bid.signum() <= 0 || ask.signum() <= 0) return unavailableExecution(request, availableAt, "empty-depth");
        List<MarketDepthLevel> entryBook = request.direction() == RecommendationAction.BUY
                ? depth.sellDepth() : depth.buyDepth();
        List<MarketDepthLevel> exitBook = request.direction() == RecommendationAction.BUY
                ? depth.buyDepth() : depth.sellDepth();
        Fill fill = fill(entryBook, request.quantity());
        long exitQuantity = visibleQuantity(exitBook);
        BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2), 12, RoundingMode.HALF_UP);
        BigDecimal spread = ask.subtract(bid).divide(mid, 12, RoundingMode.HALF_UP).multiply(BPS);
        BigDecimal touch = request.direction() == RecommendationAction.BUY ? ask : bid;
        BigDecimal impact = request.direction() == RecommendationAction.BUY
                ? fill.price().subtract(touch).max(BigDecimal.ZERO).divide(touch, 12, RoundingMode.HALF_UP).multiply(BPS)
                : touch.subtract(fill.price()).max(BigDecimal.ZERO).divide(touch, 12, RoundingMode.HALF_UP).multiply(BPS);
        BigDecimal fees = fill.price().multiply(BigDecimal.valueOf(request.quantity()))
                .multiply(executionPolicy.feesAndTaxesBps()).divide(BPS, 8, RoundingMode.HALF_UP);
        boolean entryFeasible = fill.visibleQuantity() >= request.quantity()
                && spread.compareTo(executionPolicy.maximumSpreadBps()) <= 0
                && impact.compareTo(executionPolicy.maximumSlippageBps()) <= 0;
        boolean exitFeasible = exitQuantity >= request.quantity();
        Instant validUntil = earliest(depthExpiry, availableAt.plus(executionPolicy.validity()));
        String reference = "market-depth:" + depth.snapshotId();
        var context = new ExecutionContext(depth.receivedAt(), bid, ask, reference, request.direction(),
                request.quantity(), fill.visibleQuantity(), exitQuantity, fill.price(), scale(spread), scale(impact),
                fees, scale(impact), executionPolicy.decisionToOrderLatencyMillis(), entryFeasible, exitFeasible,
                validUntil);
        GateDisposition gate = entryFeasible && exitFeasible ? GateDisposition.PASS : GateDisposition.VETO;
        String hash = hash("execution", request, depth.evidenceHash(), executionPolicy, context, gate);
        return new ExecutionEvidenceSnapshot(id("execution"), request.userId(), request.instrument(),
                AssessmentReadiness.READY, gate, context, request.analysisCutoff(), request.knowledgeCutoff(),
                availableAt, validUntil, List.of(reference), hash);
    }

    private ExecutionEvidenceSnapshot unavailableExecution(
            SafetyAssessmentRequest request, Instant availableAt, String reason) {
        Instant validUntil = availableAt.plusNanos(1);
        String reference = "unavailable:execution:" + reason;
        return new ExecutionEvidenceSnapshot(id("execution"), request.userId(), request.instrument(),
                AssessmentReadiness.UNAVAILABLE, GateDisposition.VETO, null, request.analysisCutoff(),
                request.knowledgeCutoff(), availableAt, validUntil, List.of(reference),
                hash("execution-unavailable", request, reason));
    }

    private RiskSnapshot risk(SafetyAssessmentRequest request, Optional<BrokerAccountEvidence> selected,
            ExecutionEvidenceSnapshot execution, Instant availableAt) {
        if (selected.isEmpty() || stale(selected.get().snapshot().receivedAt(), availableAt,
                riskPolicy.maximumAccountAge()) || execution.context() == null) {
            return unavailableRisk(request, selected, availableAt, "missing-stale-account-or-execution");
        }
        BrokerAccountEvidence evidence = selected.orElseThrow();
        BrokerAccountSnapshot account = evidence.snapshot();
        BigDecimal capital = availableCapital(account);
        BigDecimal dailyPnl = account.dayPositions().stream().map(BrokerPosition::pnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingLoss = riskPolicy.dailyLossLimit().add(dailyPnl.min(BigDecimal.ZERO)).max(BigDecimal.ZERO);
        int unassessedPositions = (int) account.netPositions().stream()
                .filter(position -> position.quantity() != 0).count();
        boolean unknownOpenRisk = unassessedPositions > 0;
        BigDecimal fill = execution.context().estimatedFillPrice();
        boolean validStop = request.direction() == RecommendationAction.BUY
                ? request.stopLossPrice().compareTo(fill) < 0 : request.stopLossPrice().compareTo(fill) > 0;
        BigDecimal proposedRisk = fill.subtract(request.stopLossPrice()).abs()
                .multiply(BigDecimal.valueOf(request.quantity()));
        BigDecimal proposedValue = fill.multiply(BigDecimal.valueOf(request.quantity()));
        boolean pass = execution.gateDisposition() == GateDisposition.PASS && validStop && !unknownOpenRisk
                && capital.signum() > 0 && proposedRisk.compareTo(riskPolicy.maximumRiskPerTrade()) <= 0
                && remainingLoss.signum() > 0 && proposedValue.compareTo(riskPolicy.maximumPositionValue()) <= 0;
        AssessmentReadiness readiness = unknownOpenRisk ? AssessmentReadiness.UNAVAILABLE
                : validStop ? AssessmentReadiness.READY : AssessmentReadiness.INVALID;
        GateDisposition gate = pass ? GateDisposition.PASS : GateDisposition.VETO;
        Instant validUntil = earliest(account.receivedAt().plus(riskPolicy.maximumAccountAge()),
                availableAt.plus(riskPolicy.validity()), execution.validUntil());
        if (!availableAt.isBefore(validUntil)) return unavailableRisk(request, selected, availableAt, "expired-risk-input");
        String reference = "broker-account:" + evidence.snapshotId();
        String hash = hash("risk", request, evidence.evidenceHash(), execution.evidenceHash(), riskPolicy,
                capital, proposedRisk, dailyPnl, remainingLoss, unassessedPositions, readiness, gate);
        return new RiskSnapshot(id("risk"), request.userId(), request.instrument(), readiness, gate, capital,
                riskPolicy.maximumRiskPerTrade(), proposedRisk, dailyPnl, remainingLoss, BigDecimal.ZERO,
                !unknownOpenRisk, unassessedPositions, riskPolicy.maximumOpenRisk(),
                riskPolicy.maximumPositionValue(), pass, riskPolicy.version(),
                request.analysisCutoff(), request.knowledgeCutoff(), availableAt, validUntil,
                List.of(reference, "execution:" + execution.snapshotId()), hash);
    }

    private RiskSnapshot unavailableRisk(SafetyAssessmentRequest request,
            Optional<BrokerAccountEvidence> account, Instant availableAt, String reason) {
        BigDecimal capital = account.map(value -> availableCapital(value.snapshot())).orElse(BigDecimal.ZERO);
        String reference = account.map(value -> "broker-account:" + value.snapshotId())
                .orElse("unavailable:broker-account:" + reason);
        return new RiskSnapshot(id("risk"), request.userId(), request.instrument(),
                AssessmentReadiness.UNAVAILABLE, GateDisposition.VETO, capital,
                riskPolicy.maximumRiskPerTrade(), BigDecimal.ZERO, BigDecimal.ZERO,
                riskPolicy.dailyLossLimit(), BigDecimal.ZERO, true, 0, riskPolicy.maximumOpenRisk(),
                riskPolicy.maximumPositionValue(), false, riskPolicy.version(), request.analysisCutoff(),
                request.knowledgeCutoff(), availableAt, availableAt.plusNanos(1), List.of(reference),
                hash("risk-unavailable", request, reason, account.map(BrokerAccountEvidence::evidenceHash).orElse("none")));
    }

    private PortfolioSnapshot portfolio(SafetyAssessmentRequest request,
            Optional<BrokerAccountEvidence> selected, ExecutionEvidenceSnapshot execution, Instant availableAt) {
        if (selected.isEmpty() || stale(selected.get().snapshot().receivedAt(), availableAt,
                portfolioPolicy.maximumAccountAge()) || execution.context() == null) {
            return unavailablePortfolio(request, selected, availableAt, "missing-stale-account-or-execution");
        }
        BrokerAccountEvidence evidence = selected.orElseThrow();
        BrokerAccountSnapshot account = evidence.snapshot();
        PortfolioFacts facts = portfolioFacts(account, request);
        BigDecimal fill = execution.context().estimatedFillPrice();
        BigDecimal proposedValue = fill.multiply(BigDecimal.valueOf(request.quantity()));
        BigDecimal signedChange = BigDecimal.valueOf(request.direction() == RecommendationAction.BUY
                ? request.quantity() : -request.quantity());
        BigDecimal projectedQuantity = facts.instrumentQuantity().add(signedChange);
        BigDecimal projectedInstrumentValue = projectedQuantity.abs().multiply(fill);
        BigDecimal projectedGross = facts.grossExposure().subtract(facts.instrumentMarketValue())
                .add(projectedInstrumentValue).max(BigDecimal.ZERO);
        BigDecimal projectedCash = request.direction() == RecommendationAction.BUY
                ? facts.availableCash().subtract(proposedValue).max(BigDecimal.ZERO)
                : facts.availableCash().add(proposedValue);
        BigDecimal currentTotal = facts.availableCash().add(facts.grossExposure());
        BigDecimal projectedTotal = projectedCash.add(projectedGross);
        BigDecimal weight = percent(facts.instrumentMarketValue(), currentTotal);
        BigDecimal projectedWeight = percent(projectedInstrumentValue, projectedTotal);
        BigDecimal largestWeight = percent(facts.largestPositionValue(), currentTotal);
        boolean shortViolation = !portfolioPolicy.allowShortPositions() && projectedQuantity.signum() < 0;
        boolean pass = !shortViolation
                && projectedWeight.compareTo(portfolioPolicy.maximumSinglePositionWeightPercent()) <= 0
                && largestWeight.compareTo(portfolioPolicy.maximumSinglePositionWeightPercent()) <= 0;
        GateDisposition gate = pass ? GateDisposition.PASS : GateDisposition.VETO;
        Instant validUntil = earliest(account.receivedAt().plus(portfolioPolicy.maximumAccountAge()),
                availableAt.plus(portfolioPolicy.validity()), execution.validUntil());
        if (!availableAt.isBefore(validUntil))
            return unavailablePortfolio(request, selected, availableAt, "expired-portfolio-input");
        String reference = "broker-account:" + evidence.snapshotId();
        String hash = hash("portfolio", request, evidence.evidenceHash(), execution.evidenceHash(), portfolioPolicy,
                facts, proposedValue, projectedGross, projectedWeight, gate);
        return new PortfolioSnapshot(id("portfolio"), request.userId(), request.instrument(),
                AssessmentReadiness.READY, gate, facts.availableCash(), facts.grossExposure(), facts.netExposure(),
                proposedValue, projectedGross, facts.instrumentQuantity(), facts.instrumentMarketValue(), weight,
                projectedWeight, largestWeight, portfolioPolicy.maximumSinglePositionWeightPercent(),
                facts.openPositionCount(), portfolioPolicy.version(), request.analysisCutoff(),
                request.knowledgeCutoff(), availableAt, validUntil,
                List.of(reference, "execution:" + execution.snapshotId()), hash);
    }

    private PortfolioSnapshot unavailablePortfolio(SafetyAssessmentRequest request,
            Optional<BrokerAccountEvidence> account, Instant availableAt, String reason) {
        BigDecimal cash = account.map(value -> availableCapital(value.snapshot())).orElse(BigDecimal.ZERO);
        String reference = account.map(value -> "broker-account:" + value.snapshotId())
                .orElse("unavailable:broker-account:" + reason);
        return new PortfolioSnapshot(id("portfolio"), request.userId(), request.instrument(),
                AssessmentReadiness.UNAVAILABLE, GateDisposition.VETO, cash, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, portfolioPolicy.maximumSinglePositionWeightPercent(), 0,
                portfolioPolicy.version(), request.analysisCutoff(), request.knowledgeCutoff(), availableAt,
                availableAt.plusNanos(1), List.of(reference),
                hash("portfolio-unavailable", request, reason,
                        account.map(BrokerAccountEvidence::evidenceHash).orElse("none")));
    }

    private static PortfolioFacts portfolioFacts(BrokerAccountSnapshot account, SafetyAssessmentRequest request) {
        Map<String, MutableExposure> exposures = new HashMap<>();
        for (BrokerPosition position : account.netPositions()) {
            if (position.quantity() == 0) continue;
            String key = key(position.instrument().exchange(), position.instrument().symbol());
            exposures.computeIfAbsent(key, ignored -> new MutableExposure())
                    .add(BigDecimal.valueOf(position.quantity()), position.lastPrice());
        }
        for (BrokerHolding holding : account.holdings()) {
            long quantity = holding.quantity() + holding.t1Quantity();
            if (quantity == 0) continue;
            String key = key(holding.instrument().exchange(), holding.instrument().symbol());
            exposures.computeIfAbsent(key, ignored -> new MutableExposure())
                    .add(BigDecimal.valueOf(quantity), holding.lastPrice());
        }
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal largest = BigDecimal.ZERO;
        for (MutableExposure exposure : exposures.values()) {
            BigDecimal value = exposure.quantity.multiply(exposure.lastPrice);
            gross = gross.add(value.abs());
            net = net.add(value);
            largest = largest.max(value.abs());
        }
        String targetKey = key(request.marketInstrument().exchange(), request.marketInstrument().symbol());
        MutableExposure target = exposures.getOrDefault(targetKey, new MutableExposure());
        BigDecimal targetValue = target.quantity.multiply(target.lastPrice).abs();
        return new PortfolioFacts(availableCapital(account), gross, net, target.quantity,
                targetValue, largest, exposures.size());
    }

    private static BigDecimal availableCapital(BrokerAccountSnapshot account) {
        var segment = Optional.ofNullable(account.funds().get("equity"))
                .or(() -> account.funds().values().stream().filter(value -> value.enabled()).findFirst());
        return segment.map(value -> value.available().getOrDefault("live_balance", value.net()))
                .orElse(BigDecimal.ZERO).max(BigDecimal.ZERO);
    }

    private static Fill fill(List<MarketDepthLevel> levels, int requested) {
        long visible = visibleQuantity(levels);
        long remaining = requested;
        BigDecimal value = BigDecimal.ZERO;
        long filled = 0;
        for (MarketDepthLevel level : levels) {
            if (remaining == 0 || level.price().signum() == 0 || level.quantity() == 0) continue;
            long take = Math.min(remaining, level.quantity());
            value = value.add(level.price().multiply(BigDecimal.valueOf(take)));
            filled += take;
            remaining -= take;
        }
        BigDecimal fallback = levels.getFirst().price();
        BigDecimal price = filled == 0 ? fallback : value.divide(BigDecimal.valueOf(filled), 8, RoundingMode.HALF_UP);
        return new Fill(price, visible);
    }

    private static long visibleQuantity(List<MarketDepthLevel> levels) {
        long total = 0;
        for (MarketDepthLevel level : levels) total = Math.addExact(total, level.quantity());
        return total;
    }

    private static BigDecimal percent(BigDecimal value, BigDecimal total) {
        if (value.signum() == 0) return BigDecimal.ZERO;
        if (total.signum() <= 0) return HUNDRED;
        return value.multiply(HUNDRED).divide(total, 8, RoundingMode.HALF_UP).min(HUNDRED);
    }

    private static boolean stale(Instant receivedAt, Instant now, Duration maximumAge) {
        return !now.isBefore(receivedAt.plus(maximumAge));
    }

    private static Instant earliest(Instant first, Instant... remaining) {
        Instant result = first;
        for (Instant value : remaining) if (value.isBefore(result)) result = value;
        return result;
    }

    private static BigDecimal scale(BigDecimal value) { return value.setScale(8, RoundingMode.HALF_UP); }
    private String id(String prefix) { return prefix + ":" + Objects.requireNonNull(ids.get()); }

    private static String hash(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                byte[] bytes = String.valueOf(value).getBytes(UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Fill(BigDecimal price, long visibleQuantity) { }
    private record PortfolioFacts(BigDecimal availableCash, BigDecimal grossExposure, BigDecimal netExposure,
                                  BigDecimal instrumentQuantity, BigDecimal instrumentMarketValue,
                                  BigDecimal largestPositionValue, int openPositionCount) { }
    private static final class MutableExposure {
        private BigDecimal quantity = BigDecimal.ZERO;
        private BigDecimal lastPrice = BigDecimal.ZERO;
        private void add(BigDecimal added, BigDecimal price) { quantity = quantity.add(added); lastPrice = price; }
    }
    private static String key(String exchange, String symbol) { return exchange + ":" + symbol; }
}
