package org.predictiveedge.marketintelligence.application;

import java.util.Objects;
import java.util.UUID;

public record MarketSessionPublicationResult(UUID definitionId, Status status) {
    public MarketSessionPublicationResult {
        Objects.requireNonNull(definitionId, "Session definition id is required");
        Objects.requireNonNull(status, "Publication status is required");
    }

    public enum Status {
        CREATED,
        ALREADY_PUBLISHED
    }
}
