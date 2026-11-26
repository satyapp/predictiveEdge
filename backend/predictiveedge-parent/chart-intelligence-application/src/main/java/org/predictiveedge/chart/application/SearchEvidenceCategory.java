package org.predictiveedge.chart.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Search categories that own one evidence table each. Codes are fixed; table names come only from here. */
public enum SearchEvidenceCategory {
    TOP_GAINERS, TOP_LOSERS, MOST_ACTIVE_BY_VOLUME, MOST_ACTIVE_BY_VALUE,
    WEEK_52_HIGH, WEEK_52_LOW, NEAR_WEEK_52_HIGH, NEAR_WEEK_52_LOW,
    INTRADAY_SURGE, INTRADAY_FALL, GAP_UP, GAP_DOWN, UPPER_CIRCUIT, LOWER_CIRCUIT,
    VOLUME_GAINERS, FRESH_BREAKOUTS, NEAR_BREAKOUTS, TECHNICAL_CROSSOVERS,
    PRE_OPEN;

    /** Table holding every screener row returned by a search in this category. */
    public String searchResultStockTable() {
        return "market_intelligence.search_result_stock_" + name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SearchEvidenceCategory> fromCode(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst();
    }

    /**
     * The category a broker screener run is stored under. Most NSE screen types map one to one. The two screen types
     * that cover both directions take their direction from the rank-1 row: a non-negative change is the upward
     * category, a negative change the downward one.
     */
    public static SearchEvidenceCategory forScreen(MarketScreenType screenType, List<MarketScreenObservation> rows) {
        return switch (screenType) {
            case TOP_GAINERS -> TOP_GAINERS;
            case TOP_LOSERS -> TOP_LOSERS;
            case NEW_52_WEEK_HIGH -> WEEK_52_HIGH;
            case NEW_52_WEEK_LOW -> WEEK_52_LOW;
            case MOST_ACTIVE_EQUITIES -> MOST_ACTIVE_BY_VOLUME;
            case VOLUME_GAINERS -> VOLUME_GAINERS;
            case PRICE_BAND_HITTERS -> directional(rows, UPPER_CIRCUIT, LOWER_CIRCUIT);
            case INTRADAY_OPPORTUNITY -> directional(rows, INTRADAY_SURGE, INTRADAY_FALL);
        };
    }

    private static SearchEvidenceCategory directional(List<MarketScreenObservation> rows,
            SearchEvidenceCategory upward, SearchEvidenceCategory downward) {
        if (rows.isEmpty()) throw new IllegalArgumentException("A screener run needs at least one row");
        MarketScreenObservation leader = rows.stream()
                .min(java.util.Comparator.comparingInt(MarketScreenObservation::sourceRank)).orElseThrow();
        return leader.percentChange().signum() >= 0 ? upward : downward;
    }
}
