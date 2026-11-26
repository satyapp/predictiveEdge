package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Saves the user's intraday list: the stocks of one search that were added after Stage-1 analysis. The list is
 * addressed by search and symbol, so the caller never needs the internal selection ids.
 */
public final class IntradayListRecorder {
    private final StageOneActivityStore store;

    public IntradayListRecorder(StageOneActivityStore store) {
        this.store = Objects.requireNonNull(store, "Stage-1 store is required");
    }

    /** Writes one list and returns its id. Every symbol must be a selection of {@code searchId}. */
    public UUID record(UUID userId, UUID searchId, List<String> symbols, Instant savedAt) {
        Objects.requireNonNull(userId, "User id is required");
        Objects.requireNonNull(searchId, "Search id is required");
        Objects.requireNonNull(symbols, "Symbols are required");
        Objects.requireNonNull(savedAt, "Save time is required");
        Map<String, UUID> selections = store.selectionIdsBySymbol(userId, searchId);
        List<UUID> selectionIds = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : symbols) {
            String symbol = required(value).toUpperCase(Locale.ROOT);
            UUID selectionId = selections.get(symbol);
            if (selectionId == null)
                throw new IllegalArgumentException(symbol + " was not selected from this search");
            if (seen.add(symbol)) selectionIds.add(selectionId);
        }
        UUID listId = UUID.randomUUID();
        store.recordIntradayList(new StageOneActivityStore.IntradayListSave(listId, userId, savedAt, selectionIds));
        return listId;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Symbol is required");
        return value.trim();
    }
}
