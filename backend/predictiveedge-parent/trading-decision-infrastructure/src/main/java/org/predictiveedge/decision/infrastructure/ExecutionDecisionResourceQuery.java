package org.predictiveedge.decision.infrastructure;

import java.time.Instant;
import java.util.Objects;
import org.predictiveedge.decision.application.DecisionResourceQuery;
import org.predictiveedge.decision.application.ExecutionEvidenceQueryPort;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.ExecutionEvidenceSnapshot;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.ShadowScope;

public final class ExecutionDecisionResourceQuery implements DecisionResourceQuery {
    private final ExecutionEvidenceQueryPort snapshots;
    private final ExactAiPayloadPublisher payloads;

    public ExecutionDecisionResourceQuery(ExecutionEvidenceQueryPort snapshots) {
        this(snapshots, null);
    }

    public ExecutionDecisionResourceQuery(ExecutionEvidenceQueryPort snapshots, ExactAiPayloadPublisher payloads) {
        this.snapshots = Objects.requireNonNull(snapshots, "Execution evidence query is required");
        this.payloads = payloads;
    }

    @Override public DecisionResourceType type() { return DecisionResourceType.EXECUTION; }

    @Override
    public DecisionResource findLatest(ShadowScope scope, Instant cutoff) {
        return snapshots.findLatestEvidence(scope, cutoff).map(snapshot -> publish(scope, cutoff, snapshot))
                .orElseGet(() -> unavailable(scope, cutoff));
    }

    private DecisionResource publish(ShadowScope scope, Instant cutoff, ExecutionEvidenceSnapshot snapshot) {
        DecisionResource resource = map(scope, cutoff, snapshot);
        return payloads == null ? resource : payloads.publish(scope, type(), resource, snapshot);
    }

    private static DecisionResource map(ShadowScope scope, Instant cutoff, ExecutionEvidenceSnapshot snapshot) {
        scope.requireMatches(snapshot.userId(), snapshot.instrument());
        AssessmentReadiness readiness = cutoff.isBefore(snapshot.context().validUntil())
                ? snapshot.readiness() : AssessmentReadiness.STALE;
        GateDisposition gate = snapshot.context().entryFeasible() && snapshot.context().exitFeasible()
                ? GateDisposition.PASS : GateDisposition.VETO;
        return new DecisionResource("execution:" + snapshot.snapshotId(), DecisionResourceType.EXECUTION,
                snapshot.userId(), snapshot.instrument(), readiness, gate, snapshot.analysisCutoff(),
                snapshot.knowledgeCutoff(), snapshot.availableAt(), snapshot.context().validUntil(),
                "execution-snapshot:" + snapshot.snapshotId(), snapshot.evidenceHash());
    }

    private static DecisionResource unavailable(ShadowScope scope, Instant cutoff) {
        String reference = "unavailable:execution-snapshot:" + scope.instrument().venue() + ":"
                + scope.instrument().instrumentId() + ":" + cutoff;
        return new DecisionResource("execution:unavailable:" + cutoff, DecisionResourceType.EXECUTION,
                scope.userId(), scope.instrument(), AssessmentReadiness.UNAVAILABLE, GateDisposition.VETO,
                cutoff, cutoff, cutoff, cutoff.plusNanos(1), reference, EvidenceHashing.sha256(reference));
    }
}
