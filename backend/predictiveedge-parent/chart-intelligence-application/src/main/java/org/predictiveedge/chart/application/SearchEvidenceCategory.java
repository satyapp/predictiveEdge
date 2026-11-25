package org.predictiveedge.chart.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

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
}
