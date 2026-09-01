package org.predictiveedge.marketintelligence.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.broker.domain.MarketDepthLevel;

/** Point-in-time market-depth evidence as known to the platform at {@code receivedAt}. */
public record MarketDepthSnapshot(
        UUID snapshotId,
        UUID userId,
        String brokerAccountId,
        Instrument instrument,
        String providerInstrumentId,
        List<MarketDepthLevel> buyDepth,
        List<MarketDepthLevel> sellDepth,
        Instant exchangeTimestamp,
        Instant receivedAt,
        String evidenceHash) {

    public MarketDepthSnapshot {
        Objects.requireNonNull(snapshotId, "Snapshot id is required");
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        Objects.requireNonNull(instrument, "Instrument is required");
        if (providerInstrumentId == null || providerInstrumentId.isBlank())
            throw new IllegalArgumentException("Provider instrument id is required");
        buyDepth = requireFive(buyDepth, "Buy depth");
        sellDepth = requireFive(sellDepth, "Sell depth");
        Objects.requireNonNull(exchangeTimestamp, "Exchange timestamp is required");
        Objects.requireNonNull(receivedAt, "Receipt timestamp is required");
        if (evidenceHash == null || !evidenceHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Evidence hash must be lowercase SHA-256");
    }

    private static List<MarketDepthLevel> requireFive(List<MarketDepthLevel> levels, String label) {
        levels = List.copyOf(Objects.requireNonNull(levels, label + " is required"));
        if (levels.size() != 5) throw new IllegalArgumentException(label + " must contain exactly five levels");
        return levels;
    }
}
