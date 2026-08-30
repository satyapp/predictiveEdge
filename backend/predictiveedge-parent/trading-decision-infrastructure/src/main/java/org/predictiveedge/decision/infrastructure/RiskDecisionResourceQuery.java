package org.predictiveedge.decision.infrastructure;

import java.time.Instant;
import java.util.Objects;
import org.predictiveedge.decision.application.DecisionResourceQuery;
import org.predictiveedge.decision.application.RiskSnapshotQueryPort;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.RiskSnapshot;
import org.predictiveedge.decision.domain.ShadowScope;

public final class RiskDecisionResourceQuery implements DecisionResourceQuery {
    private final RiskSnapshotQueryPort snapshots;

    public RiskDecisionResourceQuery(RiskSnapshotQueryPort snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "Risk snapshot query is required");
    }

    @Override public DecisionResourceType type() { return DecisionResourceType.RISK; }

    @Override
    public DecisionResource findLatest(ShadowScope scope, Instant cutoff) {
        return snapshots.findLatestRisk(scope, cutoff).map(snapshot -> map(scope, cutoff, snapshot))
                .orElseGet(() -> unavailable(scope, cutoff));
    }

    private static DecisionResource map(ShadowScope scope, Instant cutoff, RiskSnapshot snapshot) {
        scope.requireMatches(snapshot.userId(), snapshot.instrument());
        AssessmentReadiness readiness = cutoff.isBefore(snapshot.validUntil())
                ? snapshot.readiness() : AssessmentReadiness.STALE;
        return new DecisionResource("risk:" + snapshot.snapshotId(), DecisionResourceType.RISK,
                snapshot.userId(), snapshot.instrument(), readiness, snapshot.gateDisposition(),
                snapshot.analysisCutoff(), snapshot.knowledgeCutoff(), snapshot.availableAt(), snapshot.validUntil(),
                "risk-snapshot:" + snapshot.snapshotId(), snapshot.evidenceHash());
    }

    private static DecisionResource unavailable(ShadowScope scope, Instant cutoff) {
        String reference = "unavailable:risk-snapshot:" + scope.instrument().venue() + ":"
                + scope.instrument().instrumentId() + ":" + cutoff;
        return new DecisionResource("risk:unavailable:" + cutoff, DecisionResourceType.RISK,
                scope.userId(), scope.instrument(), AssessmentReadiness.UNAVAILABLE, GateDisposition.VETO,
                cutoff, cutoff, cutoff, cutoff.plusNanos(1), reference, EvidenceHashing.sha256(reference));
    }
}
