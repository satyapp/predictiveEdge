package org.predictiveedge.marketintelligence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityEngineTest {

    @Test
    void criticalLineageDefectCannotBeAveragedAwayByHealthyDimensions() {
        var assessment = QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(finding(QualityIssueCode.INVALID_LINEAGE, "BAR:NSE:RELIANCE")),
                List.of(fullCoverage("NSE-50"), fullCoverage("NIFTY-50")));

        assertThat(assessment.disposition()).isEqualTo(QualityDisposition.BLOCKED);
        assertThat(assessment.confidenceCap()).isZero();
        assertThat(assessment.dimensionScores().get(QualityDimension.LINEAGE).score()).isZero();
        assertThat(assessment.dimensionScores().get(QualityDimension.COVERAGE).score()).isEqualTo(100);
    }

    @Test
    void missingMandatoryEvidenceBecomesUnknownRatherThanZeroOrNeutral() {
        var featureRef = new FeatureDefinitionRef(new FeatureId("INDIA_VIX"), "1.0.0");
        var readiness = new FeatureReadinessAssessment(
                FeatureReadiness.UNAVAILABLE, 0, 1, "No eligible final observations");
        var finding = FeatureQualityFindingFactory.from(featureRef, readiness).orElseThrow();

        var assessment = QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(finding), List.of());

        assertThat(assessment.disposition()).isEqualTo(QualityDisposition.DEGRADED);
        assertThat(assessment.confidenceCap()).isEqualTo(50);
        assertThat(assessment.unknowns()).containsExactly(new QualityUnknown(
                QualityIssueCode.FEATURE_UNAVAILABLE, "INDIA_VIX@1.0.0",
                "No eligible final observations; available bars 0 of 1"));
        assertThat(assessment.dimensionScores().get(QualityDimension.COMPLETENESS).score()).isEqualTo(50);
    }

    @Test
    void coveragePublishesOriginalDenominatorExclusionsAndDegradedCap() {
        var coverage = new CoverageMeasurement("NSE-MVP-UNIVERSE", 72, 100,
                List.of(new CoverageExclusion("Suspended instruments", 10)));

        var assessment = QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(), List.of(coverage));

        assertThat(coverage.assessableCount()).isEqualTo(90);
        assertThat(coverage.coverageBasisPoints()).isEqualTo(8_000);
        assertThat(assessment.coverage()).containsExactly(coverage);
        assertThat(assessment.issues()).singleElement()
                .satisfies(issue -> assertThat(issue.finding().code()).isEqualTo(QualityIssueCode.COVERAGE_LOSS));
        assertThat(assessment.disposition()).isEqualTo(QualityDisposition.DEGRADED);
        assertThat(assessment.confidenceCap()).isEqualTo(65);
        assertThat(assessment.dimensionScores().get(QualityDimension.COVERAGE).score()).isEqualTo(70);
    }

    @Test
    void confidenceUsesTheStrictestCapInsteadOfAnAverage() {
        var findings = List.of(
                finding(QualityIssueCode.SOURCE_CONFLICT, "NSE:RELIANCE"),
                finding(QualityIssueCode.STALE_FALLBACK, "INDIA_VIX"));

        var assessment = QualityEngine.assess(
                cutoff(), QualityPolicyTest.policy(), findings, List.of());

        assertThat(assessment.confidenceCap()).isEqualTo(40);
        assertThat(assessment.disposition()).isEqualTo(QualityDisposition.DEGRADED);
    }

    @Test
    void assessmentOrderingAndHashAreIndependentOfInputOrderAndDuplicates() {
        var first = finding(QualityIssueCode.SOURCE_CONFLICT, "NSE:RELIANCE");
        var second = finding(QualityIssueCode.MANDATORY_SOURCE_MISSING, "NIFTY:BREADTH");
        var coverageA = fullCoverage("NSE-50");
        var coverageB = fullCoverage("NIFTY-50");

        var baseline = QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(first, second), List.of(coverageA, coverageB));
        var reordered = QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(second, first, first), List.of(coverageB, coverageA));

        assertThat(reordered.issues()).isEqualTo(baseline.issues());
        assertThat(reordered.coverage()).isEqualTo(baseline.coverage());
        assertThat(reordered.contentHash()).isEqualTo(baseline.contentHash());
    }

    @Test
    void healthyInputsPassWithNoUnknownsAndNoConfidenceReduction() {
        var assessment = QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(), List.of(fullCoverage("NSE-MVP-UNIVERSE")));

        assertThat(assessment.disposition()).isEqualTo(QualityDisposition.PASS);
        assertThat(assessment.confidenceCap()).isEqualTo(100);
        assertThat(assessment.unknowns()).isEmpty();
        assertThat(assessment.dimensionScores().values())
                .allSatisfy(score -> assertThat(score.score()).isEqualTo(100));
    }

    @Test
    void coverageRejectsImpossibleNumeratorsAndExclusions() {
        assertThatThrownBy(() -> new CoverageMeasurement("NSE", 91, 100,
                List.of(new CoverageExclusion("Suspended", 10))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed");
        assertThatThrownBy(() -> new CoverageMeasurement("NSE", 0, 100,
                List.of(new CoverageExclusion("Everything", 100))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assessable denominator");
        assertThatThrownBy(() -> new CoverageMeasurement("NSE", 80, 100,
                List.of(new CoverageExclusion("Suspended", 10),
                        new CoverageExclusion("Suspended", 10))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be unique");
        var duplicatedScope = fullCoverage("NSE");
        assertThatThrownBy(() -> QualityEngine.assess(cutoff(), QualityPolicyTest.policy(),
                List.of(), List.of(duplicatedScope, duplicatedScope)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scopes must be unique");
    }

    private static QualityFinding finding(QualityIssueCode code, String component) {
        return new QualityFinding(code, component, "Test finding for " + component,
                new ArrayList<>(List.of("evidence-b", "evidence-a")));
    }

    private static CoverageMeasurement fullCoverage(String scope) {
        return new CoverageMeasurement(scope, 100, 100, List.of());
    }

    private static EvaluationCutoff cutoff() {
        var time = Instant.parse("2026-08-03T04:00:00Z");
        return new EvaluationCutoff(time, time);
    }
}
