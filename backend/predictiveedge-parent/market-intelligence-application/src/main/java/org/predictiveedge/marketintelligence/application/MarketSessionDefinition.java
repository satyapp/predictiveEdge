package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.marketintelligence.domain.MarketSession;

/** One immutable, effective-dated version of a venue trading session. */
public record MarketSessionDefinition(
        UUID definitionId,
        MarketSession session,
        Instant coverageStart,
        Instant coverageEnd,
        Instant validFrom,
        Instant validTo) {

    public MarketSessionDefinition {
        Objects.requireNonNull(definitionId, "Session definition id is required");
        Objects.requireNonNull(session, "Market session is required");
        Objects.requireNonNull(coverageStart, "Coverage start is required");
        Objects.requireNonNull(coverageEnd, "Coverage end is required");
        Objects.requireNonNull(validFrom, "Valid-from time is required");
        if (!coverageStart.isBefore(coverageEnd))
            throw new IllegalArgumentException("Coverage start must precede coverage end");
        if (validTo != null && !validFrom.isBefore(validTo))
            throw new IllegalArgumentException("Valid-from time must precede valid-to time");
        if (!validFrom.isBefore(coverageEnd))
            throw new IllegalArgumentException("A session definition must become valid before coverage ends");
        if (validTo != null && !validTo.isAfter(coverageStart))
            throw new IllegalArgumentException("The validity interval must overlap the coverage interval");
        if (session.barAnchor().isBefore(coverageStart) || session.sessionEnd().isAfter(coverageEnd))
            throw new IllegalArgumentException("Coverage must contain the complete bar-producing session");
        boolean phaseOutsideCoverage = session.phaseWindows().stream().anyMatch(window ->
                window.startsAt().isBefore(coverageStart) || window.endsAt().isAfter(coverageEnd));
        if (phaseOutsideCoverage)
            throw new IllegalArgumentException("Coverage must contain every session phase window");
    }
}
