package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.predictiveedge.chart.application.SearchEvidenceCategory;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneActivityStore.EvidenceSave;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionStart;
import org.predictiveedge.chart.application.StageOneActivityStore.StockSelection;
import org.predictiveedge.intelligence.connector.EvidenceDomain;

/**
 * Persists the collector's results through the Stage-1 store. {@link #begin} records one selection per stock, then
 * each {@link #write} records one data type for one stock. Every store call is one transaction.
 */
final class StageOneStoreEvidenceWriter implements EvidenceWriter {
    private final StageOneActivityStore store;
    private final ObjectMapper json;
    private final UUID searchId;
    private final UUID userId;
    private final SearchEvidenceCategory category;
    private final Instant selectedAt;
    private final Map<String, UUID> selectionIds = new ConcurrentHashMap<>();

    StageOneStoreEvidenceWriter(StageOneActivityStore store, ObjectMapper json, UUID searchId, UUID userId,
            SearchEvidenceCategory category, Instant selectedAt) {
        this.store = Objects.requireNonNull(store, "Stage-1 store is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.searchId = Objects.requireNonNull(searchId, "Search id is required");
        this.userId = Objects.requireNonNull(userId, "User id is required");
        this.category = Objects.requireNonNull(category, "Search category is required");
        this.selectedAt = Objects.requireNonNull(selectedAt, "Selection time is required");
    }

    @Override
    public void begin(List<String> symbols) {
        List<StockSelection> stocks = new ArrayList<>();
        for (String symbol : symbols) stocks.add(new StockSelection(UUID.randomUUID(), symbol));
        store.recordSelectedStocks(new SelectionStart(searchId, userId, category, selectedAt, stocks));
        stocks.forEach(stock -> selectionIds.put(stock.symbol(), stock.selectionId()));
    }

    @Override
    public void write(String symbol, EvidenceDomain domain, ArrayNode evidence) {
        UUID selectionId = selectionIds.get(symbol);
        if (selectionId == null) throw new IllegalStateException(symbol + " has no recorded selection");
        store.recordEvidence(new EvidenceSave(selectionId, domain.name(), write(evidence)));
    }

    /** The selection id recorded for each stock, after {@link #begin} has run. */
    Map<String, UUID> selectionIds() {
        return Map.copyOf(selectionIds);
    }

    private String write(ArrayNode evidence) {
        try {
            return json.writeValueAsString(evidence);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Evidence could not be serialized", failure);
        }
    }
}
