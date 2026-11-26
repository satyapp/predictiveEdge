package org.predictiveedge.chart.application;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

/**
 * Saves one broker screener run, with every row it returned, before any stock from it is selected. Each call gets a
 * new search id, so a re-run never overwrites an earlier run.
 */
public final class ScreenerRunRecorder {
    private final StageOneActivityStore store;

    public ScreenerRunRecorder(StageOneActivityStore store) {
        this.store = Objects.requireNonNull(store, "Stage-1 store is required");
    }

    /**
     * Writes the run and returns its identity. {@code criteriaJson} is the request as a JSON object. {@code rowJson}
     * turns one returned row into its JSON object. When two rows share a symbol, the better-ranked row is kept.
     */
    public ScreenerRun record(UUID userId, String brokerAccountId, LocalDate tradingDate,
            MarketScreenDataCopy copy, String setup, String universe, String timeframe, String criteriaJson,
            Function<MarketScreenObservation, String> rowJson) {
        Objects.requireNonNull(copy, "Market-screen data copy is required");
        Objects.requireNonNull(rowJson, "Row serializer is required");
        MarketScreenType screenType = copy.screenType();
        SearchEvidenceCategory category = SearchEvidenceCategory.forScreen(screenType, copy.observations());
        UUID searchId = UUID.randomUUID();
        List<StageOneActivityStore.ScreenerRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        copy.observations().stream()
                .sorted(Comparator.comparingInt(MarketScreenObservation::sourceRank))
                .forEach(observation -> {
                    if (!seen.add(observation.symbol())) return;
                    rows.add(new StageOneActivityStore.ScreenerRow(observation.symbol(), observation.sourceRank(),
                            rowJson.apply(observation)));
                });
        store.recordSearch(new StageOneActivityStore.ScreenerSearch(searchId, userId, category, screenType,
                setup, universe, timeframe, tradingDate, brokerAccountId, criteriaJson, copy.fetchedAt(), rows));
        return new ScreenerRun(searchId, category);
    }

    /** Identity of a saved run: the search id and the category its rows are stored under. */
    public record ScreenerRun(UUID searchId, SearchEvidenceCategory category) {
        public ScreenerRun {
            Objects.requireNonNull(searchId, "Search id is required");
            Objects.requireNonNull(category, "Search category is required");
        }
    }
}
