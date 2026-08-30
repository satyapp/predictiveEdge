package org.predictiveedge.chart.application;

import java.util.List;
import java.util.Objects;
import org.predictiveedge.chart.domain.ChartAction;
import org.predictiveedge.chart.domain.ChartIntelligenceEngine;
import org.predictiveedge.chart.domain.ChartReadiness;
import org.predictiveedge.chart.domain.ChartSnapshot;
import org.predictiveedge.decision.domain.AssessmentReadiness;
import org.predictiveedge.decision.domain.GateDisposition;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.IntelligenceFeedback;
import org.predictiveedge.decision.domain.IntelligenceModule;
import org.predictiveedge.decision.domain.RecommendationAction;

/** Maps the chart authority's result into the shared, immutable decision feedback contract. */
public final class ChartFeedbackFactory {
    private final ChartIntelligenceEngine engine;

    public ChartFeedbackFactory(ChartIntelligenceEngine engine) {
        this.engine = Objects.requireNonNull(engine, "Chart intelligence engine is required");
    }

    public IntelligenceFeedback create(ChartSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "Chart snapshot is required");
        var assessment = engine.assess(snapshot);
        AssessmentReadiness readiness = AssessmentReadiness.valueOf(snapshot.readiness().name());
        GateDisposition disposition = snapshot.readiness() == ChartReadiness.READY && snapshot.finalEvidence()
                ? GateDisposition.PASS : GateDisposition.NOT_APPLICABLE;
        return new IntelligenceFeedback(snapshot.snapshotId(), IntelligenceModule.CHART,
                new InstrumentRef(snapshot.venue(), snapshot.instrumentId()), action(assessment.action()),
                assessment.confidence(), readiness, disposition, snapshot.finalEvidence(),
                snapshot.analysisCutoff(), snapshot.knowledgeCutoff(), snapshot.availableAt(), snapshot.validUntil(),
                snapshot.inputManifestHash(), List.of(assessment.reason().name()), snapshot.evidenceReferences());
    }

    private static RecommendationAction action(ChartAction action) {
        return switch (action) {
            case BUY -> RecommendationAction.BUY;
            case SELL -> RecommendationAction.SELL;
            case WAIT -> RecommendationAction.WAIT;
        };
    }
}
