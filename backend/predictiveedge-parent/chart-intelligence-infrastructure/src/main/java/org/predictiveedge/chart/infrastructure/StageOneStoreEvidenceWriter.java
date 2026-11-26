package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.SearchEvidenceCategory;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneActivityStore.EvidenceSave;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionStart;
import org.predictiveedge.chart.application.StageOneActivityStore.StockSelection;
import org.predictiveedge.intelligence.connector.EvidenceDomain;

/**
 * The database-backed {@link EvidenceStage}. {@link #begin} records one selection per stock, each {@link #write}
 * records one data type for one stock, and {@link #read} loads a stock's stored evidence back for collation. Every
 * store call is one transaction. Methods are thread-safe.
 */
final class StageOneStoreEvidenceWriter implements EvidenceStage {
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
        store.recordEvidence(new EvidenceSave(selectionId(symbol), domain.name(), write(evidence)));
    }

    @Override
    public Map<EvidenceDomain, ArrayNode> read(String symbol) {
        String byDomain = store.evidenceByDomainJson(selectionId(symbol));
        JsonNode root;
        try {
            root = json.readTree(byDomain);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored evidence could not be read", failure);
        }
        Map<EvidenceDomain, ArrayNode> evidence = new EnumMap<>(EvidenceDomain.class);
        root.fields().forEachRemaining(entry -> {
            if (!(entry.getValue() instanceof ArrayNode values))
                throw new IllegalStateException("Stored evidence for " + entry.getKey() + " is not an array");
            evidence.put(EvidenceDomain.valueOf(entry.getKey()), values);
        });
        return evidence;
    }

    /** The selection id recorded for each stock, after {@link #begin} has run. */
    Map<String, UUID> selectionIds() {
        return Map.copyOf(selectionIds);
    }

    /**
     * Saves each candidate's Stage-1 interpretation against its selection. A candidate without a selection is
     * skipped. The whole analysis is stored with every candidate, so each row traces back to one model run.
     */
    void recordAnalyses(PreOpenOllamaAnalysis analysis) {
        String analysisJson = write(analysis);
        for (PreOpenOllamaAnalysis.CandidateInterpretation candidate : analysis.candidates()) {
            UUID selectionId = selectionIds.get(candidate.symbol());
            if (selectionId == null) continue;
            store.recordAnalysis(selectionId, new StageOneActivityStore.StageOneAnalysisInput(candidate.stance(),
                    candidate.thesis(), candidate.confirmation(), candidate.risk(), analysis.model(),
                    analysis.generatedAt(), analysisJson));
        }
    }

    private UUID selectionId(String symbol) {
        UUID selectionId = selectionIds.get(symbol);
        if (selectionId == null) throw new IllegalStateException(symbol + " has no recorded selection");
        return selectionId;
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Evidence could not be serialized", failure);
        }
    }
}
