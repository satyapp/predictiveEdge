package org.predictiveedge.chart.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.chart.domain.MarketScreenType;

/**
 * Persists the user's Stage-1 activity: one screener run with every row it returned, the stocks selected from
 * that run with their evidence and AI analysis, and the intraday list handed to automation.
 *
 * <p>Each method writes in one transaction. Stage-1 rows are always marked {@code STAGE_1}.</p>
 */
public interface StageOneActivityStore {
    /** Writes the screener run and all of its rows. */
    void recordSearch(ScreenerSearch search);

    /**
     * Writes the selected stocks, their per-domain evidence and their AI analysis. A stock can be analysed once per
     * screener run; a second analysis of the same stock in the same run is rejected.
     */
    void recordSelections(SelectionBatch batch);

    /** Writes the intraday list. Every selection must belong to the user and already have Stage-1 analysis. */
    void recordIntradayList(IntradayListSave list);

    record ScreenerSearch(UUID searchId, UUID userId, SearchEvidenceCategory category, MarketScreenType screenType,
            String setup, String universe, String timeframe, LocalDate tradingDate, String brokerAccountId,
            String criteriaJson, Instant searchedAt, List<ScreenerRow> rows) {
        public ScreenerSearch {
            Objects.requireNonNull(searchId, "Search id is required");
            Objects.requireNonNull(userId, "User id is required");
            Objects.requireNonNull(category, "Search category is required");
            Objects.requireNonNull(screenType, "Screen type is required");
            Objects.requireNonNull(tradingDate, "Trading date is required");
            Objects.requireNonNull(searchedAt, "Search time is required");
            setup = trimmed(setup);
            universe = trimmed(universe);
            timeframe = trimmed(timeframe);
            brokerAccountId = required(brokerAccountId, "Broker account id");
            criteriaJson = required(criteriaJson, "Criteria JSON");
            rows = List.copyOf(Objects.requireNonNull(rows, "Screener rows are required"));
            requireDistinctSymbols(rows.stream().map(ScreenerRow::symbol).toList(), "Screener rows");
        }
    }

    record ScreenerRow(String symbol, int screenRank, String brokerRowJson) {
        public ScreenerRow {
            symbol = normalizedSymbol(symbol);
            if (screenRank < 1) throw new IllegalArgumentException("Screen rank must be positive");
            brokerRowJson = required(brokerRowJson, "Broker row JSON");
        }
    }

    record SelectionBatch(UUID searchId, UUID userId, SearchEvidenceCategory category, Instant selectedAt,
            List<SelectedStockInput> stocks) {
        public SelectionBatch {
            Objects.requireNonNull(searchId, "Search id is required");
            Objects.requireNonNull(userId, "User id is required");
            Objects.requireNonNull(category, "Search category is required");
            Objects.requireNonNull(selectedAt, "Selection time is required");
            stocks = List.copyOf(Objects.requireNonNull(stocks, "Selected stocks are required"));
            if (stocks.isEmpty()) throw new IllegalArgumentException("At least one stock must be selected");
            requireDistinctSymbols(stocks.stream().map(SelectedStockInput::symbol).toList(), "Selected stocks");
        }
    }

    /** One selected stock. {@code evidenceByDomainJson} is the JSON object keyed by evidence domain. */
    record SelectedStockInput(UUID selectionId, String symbol, String evidenceByDomainJson,
            StageOneAnalysisInput analysis) {
        public SelectedStockInput {
            Objects.requireNonNull(selectionId, "Selection id is required");
            symbol = normalizedSymbol(symbol);
            evidenceByDomainJson = required(evidenceByDomainJson, "Evidence JSON");
            Objects.requireNonNull(analysis, "Stage-1 analysis is required");
        }
    }

    record StageOneAnalysisInput(String stance, String thesis, String confirmation, String risk, String model,
            Instant generatedAt, String analysisJson) {
        public StageOneAnalysisInput {
            stance = required(stance, "Stance");
            thesis = required(thesis, "Thesis");
            confirmation = required(confirmation, "Confirmation");
            risk = required(risk, "Risk");
            model = required(model, "Model");
            Objects.requireNonNull(generatedAt, "Generated-at timestamp is required");
            analysisJson = required(analysisJson, "Analysis JSON");
        }
    }

    record IntradayListSave(UUID listId, UUID userId, Instant savedAt, List<UUID> selectionIds) {
        public IntradayListSave {
            Objects.requireNonNull(listId, "List id is required");
            Objects.requireNonNull(userId, "User id is required");
            Objects.requireNonNull(savedAt, "Save time is required");
            selectionIds = List.copyOf(Objects.requireNonNull(selectionIds, "Selection ids are required"));
            if (selectionIds.isEmpty()) throw new IllegalArgumentException("An intraday list needs at least one stock");
            if (new HashSet<>(selectionIds).size() != selectionIds.size())
                throw new IllegalArgumentException("An intraday list cannot repeat a selection");
        }
    }

    private static void requireDistinctSymbols(List<String> symbols, String label) {
        if (new HashSet<>(symbols).size() != symbols.size())
            throw new IllegalArgumentException(label + " cannot contain the same symbol twice");
    }

    private static String normalizedSymbol(String value) {
        String symbol = required(value, "Symbol").toUpperCase(Locale.ROOT);
        if (symbol.length() > 32) throw new IllegalArgumentException("Symbol must be at most 32 characters");
        return symbol;
    }

    /** Setup, universe and timeframe are optional filters; a missing value is stored as an empty string. */
    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
