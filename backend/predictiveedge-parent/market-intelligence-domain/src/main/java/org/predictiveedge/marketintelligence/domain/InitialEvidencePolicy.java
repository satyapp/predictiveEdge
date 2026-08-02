package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Versioned thresholds for the first direction, volatility and participation evidence slice. */
public record InitialEvidencePolicy(String version, BigDecimal lowAtrPercent, BigDecimal highAtrPercent,
        BigDecimal weakRelativeVolume, BigDecimal strongRelativeVolume, int uncertainty) {
    public InitialEvidencePolicy {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Evidence policy version is required");
        version = version.trim();
        Objects.requireNonNull(lowAtrPercent); Objects.requireNonNull(highAtrPercent);
        Objects.requireNonNull(weakRelativeVolume); Objects.requireNonNull(strongRelativeVolume);
        if (lowAtrPercent.signum() < 0 || lowAtrPercent.compareTo(highAtrPercent) >= 0
                || weakRelativeVolume.signum() < 0 || weakRelativeVolume.compareTo(strongRelativeVolume) >= 0)
            throw new IllegalArgumentException("Evidence thresholds are inconsistent");
        if (uncertainty < 0 || uncertainty > 100) throw new IllegalArgumentException("Evidence uncertainty is outside 0-100");
    }
}
