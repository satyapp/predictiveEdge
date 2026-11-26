package org.predictiveedge.chart.application;

import java.time.Instant;
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
import org.predictiveedge.chart.domain.PreOpenSnapshot;

/**
 * Saves one screener run, with every row it returned, before any stock from it is selected. Each call gets a new
 * search id, so a re-run never overwrites an earlier run. Broker screens and the pre-open shortlist both go through
 * here.
 */
public final class ScreenerRunRecorder {
    static final String PRE_OPEN_SCREEN = "PRE_OPEN";

    private final StageOneActivityStore store;

    public ScreenerRunRecorder(StageOneActivityStore store) {
        this.store = Objects.requireNonNull(store, "Stage-1 store is required");
    }

    /**
     * Writes a broker screen run and returns its identity. {@code criteriaJson} is the request as a JSON object.
     * {@code rowJson} turns one returned row into its JSON object. When two rows share a symbol, the better-ranked row
     * is kept.
     */
    public ScreenerRun record(UUID userId, String brokerAccountId, LocalDate tradingDate,
            MarketScreenDataCopy copy, String setup, String universe, String timeframe, String criteriaJson,
            Function<MarketScreenObservation, String> rowJson) {
        Objects.requireNonNull(copy, "Market-screen data copy is required");
        Objects.requireNonNull(rowJson, "Row serializer is required");
        MarketScreenType screenType = copy.screenType();
        SearchEvidenceCategory category = SearchEvidenceCategory.forScreen(screenType, copy.observations());
        List<StageOneActivityStore.ScreenerRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        copy.observations().stream()
                .sorted(Comparator.comparingInt(MarketScreenObservation::sourceRank))
                .forEach(observation -> {
                    if (!seen.add(observation.symbol())) return;
                    rows.add(new StageOneActivityStore.ScreenerRow(observation.symbol(), observation.sourceRank(),
                            rowJson.apply(observation)));
                });
        return save(userId, category, screenType.name(), brokerAccountId, tradingDate, setup, universe, timeframe,
                criteriaJson, copy.fetchedAt(), rows);
    }

    /**
     * Writes the pre-open shortlist as a run in the PRE_OPEN category. The shortlist is already ordered by rank, so
     * its position is the rank.
     */
    public ScreenerRun recordPreOpen(UUID userId, String brokerAccountId, LocalDate tradingDate,
            List<PreOpenSnapshot> shortlist, String criteriaJson, Function<PreOpenSnapshot, String> rowJson,
            Instant searchedAt) {
        Objects.requireNonNull(shortlist, "Pre-open shortlist is required");
        Objects.requireNonNull(rowJson, "Row serializer is required");
        List<StageOneActivityStore.ScreenerRow> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (PreOpenSnapshot snapshot : shortlist) {
            if (!seen.add(snapshot.symbol())) continue;
            rows.add(new StageOneActivityStore.ScreenerRow(snapshot.symbol(), rows.size() + 1,
                    rowJson.apply(snapshot), snapshot.detail()));
        }
        return save(userId, SearchEvidenceCategory.PRE_OPEN, PRE_OPEN_SCREEN, brokerAccountId, tradingDate, "", "",
                "", criteriaJson, searchedAt, rows);
    }

    private ScreenerRun save(UUID userId, SearchEvidenceCategory category, String screenType, String brokerAccountId,
            LocalDate tradingDate, String setup, String universe, String timeframe, String criteriaJson,
            Instant searchedAt, List<StageOneActivityStore.ScreenerRow> rows) {
        UUID searchId = UUID.randomUUID();
        store.recordSearch(new StageOneActivityStore.ScreenerSearch(searchId, userId, category, screenType, setup,
                universe, timeframe, tradingDate, brokerAccountId, criteriaJson, searchedAt, rows));
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
