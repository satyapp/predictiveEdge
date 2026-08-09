package org.predictiveedge.marketintelligence.application;

import java.util.Objects;

/** Operator-facing use case for publishing governed market-calendar versions. */
public final class MarketSessionCalendarService {
    private final MarketSessionPublicationPort publications;

    public MarketSessionCalendarService(MarketSessionPublicationPort publications) {
        this.publications = Objects.requireNonNull(publications, "Session publication port is required");
    }

    public MarketSessionPublicationResult publish(MarketSessionDefinition definition) {
        return publications.publish(Objects.requireNonNull(definition, "Session definition is required"));
    }
}
