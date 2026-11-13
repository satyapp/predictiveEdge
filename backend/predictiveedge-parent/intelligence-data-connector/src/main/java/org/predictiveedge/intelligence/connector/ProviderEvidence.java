package org.predictiveedge.intelligence.connector;

import java.time.Instant;
import java.util.Objects;

public record ProviderEvidence(
        EvidenceDomain domain,
        EvidenceSource source,
        SourceRole role,
        EvidenceFetchStatus status,
        Instant fetchedAt,
        Object payload,
        String detail) {
    public ProviderEvidence {
        Objects.requireNonNull(domain, "Evidence domain is required");
        Objects.requireNonNull(source, "Evidence source is required");
        Objects.requireNonNull(role, "Source role is required");
        Objects.requireNonNull(status, "Fetch status is required");
        Objects.requireNonNull(fetchedAt, "Fetch time is required");
        detail = detail == null ? "" : detail;
        if (status == EvidenceFetchStatus.SUCCESS && payload == null) {
            throw new IllegalArgumentException("Successful evidence requires a payload");
        }
    }
}
