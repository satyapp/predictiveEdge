package org.predictiveedge.marketintelligence.domain;

/** Versioned conflict tolerance for evidence fusion. */
public record FusionPolicy(String version, int conflictTolerance) {
    public FusionPolicy {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Fusion policy version is required");
        version = version.trim();
        if (conflictTolerance < 0 || conflictTolerance > 100) {
            throw new IllegalArgumentException("Conflict tolerance must be between 0 and 100");
        }
    }
}
