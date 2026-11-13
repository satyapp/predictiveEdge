package org.predictiveedge.intelligence.connector;

import java.util.Objects;

public record EvidenceRoute(EvidenceDomain domain, EvidenceSource source, SourceRole role, int priority) {
    public EvidenceRoute {
        Objects.requireNonNull(domain, "Evidence domain is required");
        Objects.requireNonNull(source, "Evidence source is required");
        Objects.requireNonNull(role, "Source role is required");
        if (priority < 1) throw new IllegalArgumentException("Source priority must be positive");
    }
}
