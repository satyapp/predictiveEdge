package org.predictiveedge.chart.infrastructure;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceFetchStatus;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.IntelligenceDataConnector;
import org.predictiveedge.intelligence.connector.ProviderEvidence;
import org.predictiveedge.intelligence.connector.ResourceEvidenceBundle;
import org.predictiveedge.intelligence.connector.SourceRole;

/**
 * Supplies the pre-open market data of a shortlist as the only evidence for its selected stocks. Every other data
 * type is empty, so no provider is called for a pre-open run. The data comes from the NSE pre-open CSV that the
 * caller has already parsed and stored.
 */
final class PreOpenEvidenceConnector implements IntelligenceDataConnector {
    private final Map<String, PreOpenSnapshot> snapshots;

    PreOpenEvidenceConnector(Map<String, PreOpenSnapshot> snapshots) {
        this.snapshots = Map.copyOf(Objects.requireNonNull(snapshots, "Pre-open snapshots are required"));
    }

    @Override
    public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (domain != EvidenceDomain.PRE_OPEN_ANALYSIS) return List.of();
        PreOpenSnapshot snapshot = snapshots.get(query.symbol());
        if (snapshot == null) return List.of();
        return List.of(new ProviderEvidence(domain, EvidenceSource.NSE, SourceRole.PRIMARY,
                EvidenceFetchStatus.SUCCESS, snapshot.observedAt(), payload(snapshot),
                "NSE pre-open auction snapshot from the uploaded CSV"));
    }

    @Override
    public ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query) {
        throw new UnsupportedOperationException("Pre-open evidence is fetched per data type");
    }

    /** Plain values only, so the payload serializes the same way with or without date-time modules. */
    static Map<String, Object> payload(PreOpenSnapshot snapshot) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "PRE_OPEN_AUCTION_SNAPSHOT");
        payload.put("venue", snapshot.venue());
        payload.put("symbol", snapshot.symbol());
        payload.put("series", snapshot.series());
        payload.put("previous_close", decimal(snapshot.previousClose()));
        payload.put("auction_price", decimal(snapshot.auctionPrice()));
        payload.put("auction_volume", snapshot.auctionVolume());
        payload.put("auction_turnover", decimal(snapshot.auctionTurnover()));
        payload.put("best_bid", decimal(snapshot.bestBid()));
        payload.put("best_ask", decimal(snapshot.bestAsk()));
        payload.put("observed_at", snapshot.observedAt().toString());
        return payload;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
