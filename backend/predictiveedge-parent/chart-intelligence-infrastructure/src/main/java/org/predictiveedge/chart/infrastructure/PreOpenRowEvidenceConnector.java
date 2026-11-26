package org.predictiveedge.chart.infrastructure;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.domain.PreOpenAuctionDetail;
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
 * Gives each selected stock of a search its pre-open row for the search's trading day, as the pre-open evidence of
 * that stock. Every other data type comes from the delegate. When the stock has no pre-open row, the delegate's
 * answer is used unchanged.
 */
final class PreOpenRowEvidenceConnector implements IntelligenceDataConnector {
    private final IntelligenceDataConnector delegate;
    private final StageOneActivityStore store;
    private final UUID userId;
    private final UUID searchId;

    PreOpenRowEvidenceConnector(IntelligenceDataConnector delegate, StageOneActivityStore store, UUID userId,
            UUID searchId) {
        this.delegate = Objects.requireNonNull(delegate, "Delegate connector is required");
        this.store = Objects.requireNonNull(store, "Activity store is required");
        this.userId = Objects.requireNonNull(userId, "User id is required");
        this.searchId = Objects.requireNonNull(searchId, "Search id is required");
    }

    @Override
    public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
        if (domain != EvidenceDomain.PRE_OPEN_ANALYSIS) return delegate.fetch(domain, query);
        return store.preOpenRowForSearch(userId, searchId, query.symbol())
                .map(row -> List.of(new ProviderEvidence(domain, EvidenceSource.NSE, SourceRole.PRIMARY,
                        EvidenceFetchStatus.SUCCESS, row.storedAt(), payload(row),
                        "NSE pre-open row of the trading day, from the stored pre-open run")))
                .orElseGet(() -> delegate.fetch(domain, query));
    }

    @Override
    public ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query) {
        return delegate.fetchForResource(resourceType, query);
    }

    /** Plain values only, the same shape as the pre-open snapshot payload. */
    static Map<String, Object> payload(StageOneActivityStore.PreOpenRow row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "PRE_OPEN_SEARCH_RESULT");
        payload.put("pre_open_search_id", row.preOpenSearchId().toString());
        payload.put("trading_date", row.tradingDate().toString());
        payload.put("symbol", row.symbol());
        payload.put("screen_rank", row.screenRank());
        payload.put("broker_row", row.brokerRowJson());
        PreOpenAuctionDetail detail = row.detail();
        payload.put("previous_close", decimal(detail.previousClose()));
        payload.put("best_bid_price", decimal(detail.bestBidPrice()));
        payload.put("best_bid_qty", detail.bestBidQuantity());
        payload.put("best_ask_price", decimal(detail.bestAskPrice()));
        payload.put("best_ask_qty", detail.bestAskQuantity());
        payload.put("indicative_equilibrium_price", decimal(detail.indicativeEquilibriumPrice()));
        payload.put("indicative_equilibrium_qty", detail.indicativeEquilibriumQuantity());
        payload.put("price_change", decimal(detail.priceChange()));
        payload.put("percent_change", decimal(detail.percentChange()));
        payload.put("final_price", decimal(detail.finalPrice()));
        payload.put("final_volume", detail.finalVolume());
        payload.put("traded_value", decimal(detail.tradedValue()));
        payload.put("ffm_cap", decimal(detail.ffmCap()));
        payload.put("indicative_imbalance_qty_at_equilibrium", detail.indicativeImbalanceQuantityAtEquilibriumPrice());
        payload.put("indicative_imbalance_qty_at_market_order", detail.indicativeImbalanceQuantityAtMarketOrder());
        payload.put("week_52_high", decimal(detail.week52High()));
        payload.put("week_52_low", decimal(detail.week52Low()));
        return payload;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
