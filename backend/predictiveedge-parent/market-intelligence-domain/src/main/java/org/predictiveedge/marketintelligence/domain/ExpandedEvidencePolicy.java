package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Governed thresholds for translating the complete initial indicator profile into typed evidence. */
public record ExpandedEvidencePolicy(String version, BigDecimal strongAdx, BigDecimal lowBandWidth,
        BigDecimal highBandWidth, BigDecimal valueTolerancePercent, BigDecimal bearishRsi, BigDecimal bullishRsi,
        BigDecimal lowAtrPercent, BigDecimal highAtrPercent, BigDecimal leadershipTolerancePercent,
        BigDecimal bearishBreadthPercent, BigDecimal bullishBreadthPercent, int uncertainty) {
    public ExpandedEvidencePolicy {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Evidence policy version is required");
        version = version.trim();
        var values = List.of(strongAdx, lowBandWidth, highBandWidth, valueTolerancePercent, bearishRsi, bullishRsi,
                lowAtrPercent, highAtrPercent, leadershipTolerancePercent, bearishBreadthPercent, bullishBreadthPercent);
        if (values.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Evidence thresholds are required");
        if (strongAdx.signum() < 0 || lowBandWidth.signum() < 0 || valueTolerancePercent.signum() < 0
                || lowAtrPercent.signum() < 0 || leadershipTolerancePercent.signum() < 0
                || lowBandWidth.compareTo(highBandWidth) >= 0 || bearishRsi.compareTo(bullishRsi) >= 0
                || lowAtrPercent.compareTo(highAtrPercent) >= 0
                || bearishRsi.signum() < 0 || bullishRsi.compareTo(BigDecimal.valueOf(100)) > 0
                || bearishBreadthPercent.compareTo(BigDecimal.valueOf(-100)) < 0
                || bullishBreadthPercent.compareTo(BigDecimal.valueOf(100)) > 0
                || bearishBreadthPercent.compareTo(bullishBreadthPercent) >= 0 || uncertainty < 0 || uncertainty > 100)
            throw new IllegalArgumentException("Evidence thresholds are inconsistent");
    }
}
