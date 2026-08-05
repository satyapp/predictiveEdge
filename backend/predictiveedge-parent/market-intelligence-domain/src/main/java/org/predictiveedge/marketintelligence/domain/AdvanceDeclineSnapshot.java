package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Point-in-time advance/decline result with its complete universe coverage denominator. */
public record AdvanceDeclineSnapshot(String universeId, String universeVersion, EvaluationCutoff cutoff,
        int advances, int declines,
        int unchanged, CoverageMeasurement coverage, BigDecimal netBreadthPercent, ContentHash inputManifestHash) {
    public AdvanceDeclineSnapshot {
        if (universeId == null || universeId.isBlank() || universeVersion == null || universeVersion.isBlank())
            throw new IllegalArgumentException("Universe identity and version are required");
        Objects.requireNonNull(cutoff); Objects.requireNonNull(coverage); Objects.requireNonNull(netBreadthPercent);
        Objects.requireNonNull(inputManifestHash);
        if (advances < 0 || declines < 0 || unchanged < 0 || advances + declines + unchanged != coverage.receivedCount())
            throw new IllegalArgumentException("Breadth counts do not match coverage");
    }

    public static AdvanceDeclineSnapshot calculate(String universeId, String universeVersion, EvaluationCutoff cutoff,
            int expectedMembers, List<CoverageExclusion> exclusions, List<BreadthConstituent> constituents,
            ContentHash inputManifestHash) {
        Objects.requireNonNull(cutoff); Objects.requireNonNull(constituents);
        var identities = new HashSet<ObservationSubject>(); int advances = 0, declines = 0, unchanged = 0;
        for (BreadthConstituent value : constituents) {
            if (!identities.add(value.subject())) throw new IllegalArgumentException("Duplicate breadth constituent");
            if (value.eventTime().isAfter(cutoff.analysisCutoff())
                    || value.availableAt().isAfter(cutoff.knowledgeCutoff()))
                throw new IllegalArgumentException("Breadth constituent is not causally eligible");
            int comparison = value.currentClose().compareTo(value.priorClose());
            if (comparison > 0) advances++; else if (comparison < 0) declines++; else unchanged++;
        }
        var coverage = new CoverageMeasurement(universeId, constituents.size(), expectedMembers, exclusions);
        BigDecimal net = BigDecimal.valueOf(advances - declines).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(coverage.assessableCount()), MathContext.DECIMAL128);
        return new AdvanceDeclineSnapshot(universeId.trim(), universeVersion.trim(), cutoff, advances, declines, unchanged,
                coverage, net, inputManifestHash);
    }
}
