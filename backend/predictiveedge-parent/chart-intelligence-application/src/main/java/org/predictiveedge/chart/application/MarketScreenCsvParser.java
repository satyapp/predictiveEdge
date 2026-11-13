package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Adapter contract for one or more concrete NSE market-screen CSV schemas. */
public interface MarketScreenCsvParser {
    Set<MarketScreenType> supportedTypes();
    ParseResult parse(MarketScreenType type, String csv, Instant observedAt);

    record ParseResult(List<MarketScreenObservation> observations, List<RowRejection> rejectedRows) {
        public ParseResult {
            observations = List.copyOf(observations);
            rejectedRows = List.copyOf(rejectedRows);
        }
    }

    record RowRejection(int rowNumber, String symbol, String reason) { }
}
