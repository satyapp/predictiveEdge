package org.predictiveedge.decision.infrastructure;

import java.time.Instant;
import java.util.Objects;
import org.predictiveedge.chart.application.ChartSnapshotQueryPort;
import org.predictiveedge.chart.domain.ChartReadiness;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.decision.application.DecisionResourceQuery;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.DecisionResource;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.ShadowScope;

/** Translates a persisted chart snapshot into the AI-neutral decision-resource envelope. */
public final class ChartDecisionResourceQuery implements DecisionResourceQuery {
    private final ChartSnapshotQueryPort snapshots;

    public ChartDecisionResourceQuery(ChartSnapshotQueryPort snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "Chart snapshot query is required");
    }

    @Override
    public DecisionResourceType type() {
        return DecisionResourceType.CHART;
    }

    @Override
    public DecisionResource findLatest(ShadowScope scope, Instant cutoff) {
        Objects.requireNonNull(scope, "Shadow scope is required");
        Objects.requireNonNull(cutoff, "Evidence cutoff is required");
        return snapshots.findLatest(scope.userId(), scope.instrument().venue(),
                        scope.instrument().instrumentId(), cutoff)
                .map(snapshot -> map(scope, snapshot))
                .orElseGet(() -> unavailable(scope, cutoff));
    }

    private static DecisionResource map(ShadowScope scope, ChartSnapshot snapshot) {
        if (!snapshot.venue().equals(scope.instrument().venue())
                || !snapshot.instrumentId().equals(scope.instrument().instrumentId())) {
            throw new IllegalArgumentException("Chart snapshot does not match the fixed shadow equity");
        }
        return new DecisionResource("chart:" + snapshot.snapshotId(), DecisionResourceType.CHART,
                scope.userId(), scope.instrument(), readiness(snapshot.readiness()), GateDisposition.PASS,
                snapshot.analysisCutoff(), snapshot.knowledgeCutoff(), snapshot.availableAt(), snapshot.validUntil(),
                "chart-snapshot:" + snapshot.snapshotId(), hash(snapshot));
    }

    private static DecisionResource unavailable(ShadowScope scope, Instant cutoff) {
        String reference = "unavailable:chart-snapshot:" + scope.instrument().venue() + ":"
                + scope.instrument().instrumentId() + ":" + cutoff;
        return new DecisionResource("chart:unavailable:" + cutoff, DecisionResourceType.CHART,
                scope.userId(), scope.instrument(), AssessmentReadiness.UNAVAILABLE, GateDisposition.NOT_APPLICABLE,
                cutoff, cutoff, cutoff, cutoff.plusNanos(1), reference, EvidenceHashing.sha256(reference));
    }

    private static AssessmentReadiness readiness(ChartReadiness readiness) {
        return AssessmentReadiness.valueOf(readiness.name());
    }

    private static String hash(ChartSnapshot snapshot) {
        return EvidenceHashing.sha256(String.join("|", snapshot.snapshotId(), snapshot.venue(),
                snapshot.instrumentId(), snapshot.higherTimeframeBias().name(),
                snapshot.sessionLocationBias().name(), snapshot.momentumBias().name(), snapshot.trigger().name(),
                Boolean.toString(snapshot.trendQualified()), Boolean.toString(snapshot.regimePermitsSetup()),
                Boolean.toString(snapshot.participationConfirmed()), Integer.toString(snapshot.confidence()),
                snapshot.readiness().name(), Boolean.toString(snapshot.finalEvidence()),
                snapshot.analysisCutoff().toString(), snapshot.knowledgeCutoff().toString(),
                snapshot.availableAt().toString(), snapshot.validUntil().toString(), snapshot.inputManifestHash(),
                String.join(",", snapshot.evidenceReferences())));
    }
}
