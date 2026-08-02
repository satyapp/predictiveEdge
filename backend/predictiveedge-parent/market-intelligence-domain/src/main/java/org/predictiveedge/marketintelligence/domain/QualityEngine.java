package org.predictiveedge.marketintelligence.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** Pure deterministic evaluator for hard quality gates, degraded modes, coverage and caps. */
public final class QualityEngine {
    private QualityEngine() {
    }

    public static QualityAssessment assess(
            EvaluationCutoff cutoff,
            QualityPolicy policy,
            Collection<QualityFinding> suppliedFindings,
            Collection<CoverageMeasurement> suppliedCoverage) {
        Objects.requireNonNull(cutoff, "Quality evaluation cutoff is required");
        Objects.requireNonNull(policy, "Quality policy is required");
        requireNoNulls(suppliedFindings, "Quality findings");
        requireNoNulls(suppliedCoverage, "Coverage measurements");

        var coverage = suppliedCoverage.stream().sorted(Comparator.comparing(CoverageMeasurement::scope)).toList();
        if (coverage.stream().map(CoverageMeasurement::scope).distinct().count() != coverage.size()) {
            throw new IllegalArgumentException("Coverage scopes must be unique within an assessment");
        }
        var findings = new LinkedHashSet<>(suppliedFindings);
        coverage.stream()
                .filter(value -> value.coverageBasisPoints() < policy.minimumCoverageBasisPoints())
                .map(value -> coverageFinding(value, policy.minimumCoverageBasisPoints()))
                .forEach(findings::add);

        var issues = findings.stream()
                .map(finding -> QualityIssue.assess(finding, policy.ruleFor(finding.code())))
                .toList();
        var disposition = disposition(issues);
        int confidenceCap = issues.stream().mapToInt(QualityIssue::confidenceCap).min().orElse(100);
        var unknowns = issues.stream().filter(QualityIssue::producesUnknown)
                .map(issue -> new QualityUnknown(issue.finding().code(),
                        issue.finding().affectedComponent(), issue.finding().detail()))
                .toList();
        var scores = dimensionScores(issues, coverage);
        return QualityAssessment.create(cutoff, policy.version(), disposition, issues, coverage,
                scores, confidenceCap, unknowns);
    }

    private static QualityFinding coverageFinding(CoverageMeasurement value, int threshold) {
        var evidence = value.exclusions().stream()
                .map(exclusion -> exclusion.reason() + "=" + exclusion.count()).toList();
        String detail = "Received " + value.receivedCount() + " of " + value.expectedCount()
                + " expected; excluded " + value.excludedCount() + "; coverage "
                + value.coverageBasisPoints() + "bp is below " + threshold + "bp";
        return new QualityFinding(QualityIssueCode.COVERAGE_LOSS, value.scope(), detail, evidence);
    }

    private static QualityDisposition disposition(List<QualityIssue> issues) {
        if (issues.stream().anyMatch(issue -> issue.action() == QualityAction.BLOCK)) {
            return QualityDisposition.BLOCKED;
        }
        if (issues.stream().anyMatch(issue -> issue.action() == QualityAction.DEGRADE)) {
            return QualityDisposition.DEGRADED;
        }
        return QualityDisposition.PASS;
    }

    private static EnumMap<QualityDimension, QualityDimensionScore> dimensionScores(
            List<QualityIssue> issues,
            List<CoverageMeasurement> coverage) {
        var result = new EnumMap<QualityDimension, QualityDimensionScore>(QualityDimension.class);
        for (QualityDimension dimension : QualityDimension.values()) {
            var dimensionIssues = issues.stream().filter(issue -> issue.dimension() == dimension).toList();
            int penalty = dimensionIssues.stream().mapToInt(QualityIssue::dimensionPenalty).sum();
            int score = Math.max(0, 100 - penalty);
            if (dimension == QualityDimension.COVERAGE && !coverage.isEmpty()) {
                int coverageScore = coverage.stream().mapToInt(CoverageMeasurement::coverageBasisPoints)
                        .min().orElse(10_000) / 100;
                score = Math.min(score, coverageScore);
            }
            var codes = dimensionIssues.stream().map(issue -> issue.finding().code()).toList();
            result.put(dimension, new QualityDimensionScore(dimension, score, codes));
        }
        return result;
    }

    private static <T> void requireNoNulls(Collection<T> values, String label) {
        Objects.requireNonNull(values, label + " are required");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(label + " cannot contain null");
        }
    }
}
