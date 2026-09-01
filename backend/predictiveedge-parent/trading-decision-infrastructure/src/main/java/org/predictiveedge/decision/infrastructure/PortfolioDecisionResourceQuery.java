package org.predictiveedge.decision.infrastructure;

import java.time.Instant;
import java.util.Objects;
import org.predictiveedge.decision.application.DecisionResourceQuery;
import org.predictiveedge.decision.application.PortfolioSnapshotQueryPort;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.PortfolioSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;

public final class PortfolioDecisionResourceQuery implements DecisionResourceQuery {
    private final PortfolioSnapshotQueryPort snapshots;
    private final ExactAiPayloadPublisher payloads;

    public PortfolioDecisionResourceQuery(PortfolioSnapshotQueryPort snapshots) {
        this(snapshots, null);
    }

    public PortfolioDecisionResourceQuery(PortfolioSnapshotQueryPort snapshots, ExactAiPayloadPublisher payloads) {
        this.snapshots = Objects.requireNonNull(snapshots, "Portfolio snapshot query is required");
        this.payloads = payloads;
    }

    @Override public DecisionResourceType type() { return DecisionResourceType.PORTFOLIO; }

    @Override
    public DecisionResource findLatest(ShadowScope scope, Instant cutoff) {
        return snapshots.findLatestPortfolio(scope, cutoff).map(snapshot -> publish(scope, cutoff, snapshot))
                .orElseGet(() -> unavailable(scope, cutoff));
    }

    private DecisionResource publish(ShadowScope scope, Instant cutoff, PortfolioSnapshot snapshot) {
        DecisionResource resource = map(scope, cutoff, snapshot);
        return payloads == null ? resource : payloads.publish(scope, type(), resource, snapshot);
    }

    private static DecisionResource map(ShadowScope scope, Instant cutoff, PortfolioSnapshot snapshot) {
        scope.requireMatches(snapshot.userId(), snapshot.instrument());
        AssessmentReadiness readiness = cutoff.isBefore(snapshot.validUntil())
                ? snapshot.readiness() : AssessmentReadiness.STALE;
        return new DecisionResource("portfolio:" + snapshot.snapshotId(), DecisionResourceType.PORTFOLIO,
                snapshot.userId(), snapshot.instrument(), readiness, snapshot.gateDisposition(),
                snapshot.analysisCutoff(), snapshot.knowledgeCutoff(), snapshot.availableAt(), snapshot.validUntil(),
                "portfolio-snapshot:" + snapshot.snapshotId(), snapshot.evidenceHash());
    }

    private static DecisionResource unavailable(ShadowScope scope, Instant cutoff) {
        String reference = "unavailable:portfolio-snapshot:" + scope.instrument().venue() + ":"
                + scope.instrument().instrumentId() + ":" + cutoff;
        return new DecisionResource("portfolio:unavailable:" + cutoff, DecisionResourceType.PORTFOLIO,
                scope.userId(), scope.instrument(), AssessmentReadiness.UNAVAILABLE, GateDisposition.VETO,
                cutoff, cutoff, cutoff, cutoff.plusNanos(1), reference, EvidenceHashing.sha256(reference));
    }
}
