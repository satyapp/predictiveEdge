package org.predictiveedge.marketintelligence.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable quality-gate result with canonical ordering and integrity hash. */
public record QualityAssessment(
        EvaluationCutoff cutoff,
        String policyVersion,
        QualityDisposition disposition,
        List<QualityIssue> issues,
        List<CoverageMeasurement> coverage,
        Map<QualityDimension, QualityDimensionScore> dimensionScores,
        int confidenceCap,
        List<QualityUnknown> unknowns,
        ContentHash contentHash) {

    public QualityAssessment {
        Objects.requireNonNull(cutoff, "Quality assessment cutoff is required");
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("Quality policy version is required");
        }
        policyVersion = policyVersion.trim();
        Objects.requireNonNull(disposition, "Quality disposition is required");
        issues = List.copyOf(Objects.requireNonNull(issues, "Quality issues are required").stream()
                .sorted(issueComparator()).toList());
        coverage = List.copyOf(Objects.requireNonNull(coverage, "Coverage measurements are required").stream()
                .sorted(Comparator.comparing(CoverageMeasurement::scope)).toList());
        var scoreCopy = new EnumMap<QualityDimension, QualityDimensionScore>(QualityDimension.class);
        scoreCopy.putAll(Objects.requireNonNull(dimensionScores, "Quality dimension scores are required"));
        for (QualityDimension dimension : QualityDimension.values()) {
            var score = scoreCopy.get(dimension);
            if (score == null || score.dimension() != dimension) {
                throw new IllegalArgumentException("Missing or inconsistent score for " + dimension);
            }
        }
        dimensionScores = Map.copyOf(scoreCopy);
        if (confidenceCap < 0 || confidenceCap > 100) {
            throw new IllegalArgumentException("Quality confidence cap must be between 0 and 100");
        }
        unknowns = List.copyOf(Objects.requireNonNull(unknowns, "Quality unknowns are required").stream()
                .distinct().sorted(Comparator.comparing((QualityUnknown value) -> value.cause().name())
                        .thenComparing(QualityUnknown::affectedComponent)
                        .thenComparing(QualityUnknown::reason)).toList());
        Objects.requireNonNull(contentHash, "Quality assessment hash is required");
        if (!contentHash.equals(hash(cutoff, policyVersion, disposition, issues, coverage,
                dimensionScores, confidenceCap, unknowns))) {
            throw new IllegalArgumentException("Quality assessment hash does not match its contents");
        }
    }

    static QualityAssessment create(
            EvaluationCutoff cutoff,
            String policyVersion,
            QualityDisposition disposition,
            List<QualityIssue> issues,
            List<CoverageMeasurement> coverage,
            Map<QualityDimension, QualityDimensionScore> dimensionScores,
            int confidenceCap,
            List<QualityUnknown> unknowns) {
        return new QualityAssessment(cutoff, policyVersion, disposition, issues, coverage, dimensionScores,
                confidenceCap, unknowns, hash(cutoff, policyVersion, disposition, issues, coverage,
                        dimensionScores, confidenceCap, unknowns));
    }

    private static ContentHash hash(
            EvaluationCutoff cutoff,
            String policyVersion,
            QualityDisposition disposition,
            List<QualityIssue> issues,
            List<CoverageMeasurement> coverage,
            Map<QualityDimension, QualityDimensionScore> scores,
            int confidenceCap,
            List<QualityUnknown> unknowns) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, cutoff.analysisCutoff().toString());
            add(digest, cutoff.knowledgeCutoff().toString());
            add(digest, policyVersion);
            add(digest, disposition.name());
            for (QualityIssue issue : issues.stream().sorted(issueComparator()).toList()) {
                add(digest, issue.finding().code().name());
                add(digest, issue.finding().affectedComponent());
                add(digest, issue.finding().detail());
                issue.finding().evidenceRefs().forEach(value -> add(digest, value));
                add(digest, issue.severity().name());
                add(digest, issue.action().name());
                add(digest, Integer.toString(issue.dimensionPenalty()));
                add(digest, Integer.toString(issue.confidenceCap()));
                add(digest, Boolean.toString(issue.producesUnknown()));
            }
            for (CoverageMeasurement measurement : coverage.stream()
                    .sorted(Comparator.comparing(CoverageMeasurement::scope)).toList()) {
                add(digest, measurement.scope());
                add(digest, Integer.toString(measurement.receivedCount()));
                add(digest, Integer.toString(measurement.expectedCount()));
                for (CoverageExclusion exclusion : measurement.exclusions()) {
                    add(digest, exclusion.reason());
                    add(digest, Integer.toString(exclusion.count()));
                }
            }
            for (QualityDimension dimension : QualityDimension.values()) {
                var score = scores.get(dimension);
                add(digest, dimension.name());
                add(digest, Integer.toString(score.score()));
                score.issueCodes().forEach(code -> add(digest, code.name()));
            }
            add(digest, Integer.toString(confidenceCap));
            unknowns.stream().distinct()
                    .sorted(Comparator.comparing((QualityUnknown value) -> value.cause().name())
                            .thenComparing(QualityUnknown::affectedComponent)
                            .thenComparing(QualityUnknown::reason))
                    .forEach(unknown -> {
                        add(digest, unknown.cause().name());
                        add(digest, unknown.affectedComponent());
                        add(digest, unknown.reason());
                    });
            return new ContentHash(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Comparator<QualityIssue> issueComparator() {
        return Comparator.comparing((QualityIssue issue) -> issue.finding().code().name())
                .thenComparing(issue -> issue.finding().affectedComponent())
                .thenComparing(issue -> issue.finding().detail())
                .thenComparing(issue -> String.join("\u0000", issue.finding().evidenceRefs()));
    }

    private static void add(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
