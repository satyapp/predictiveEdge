package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Canonical PredictiveEdge intake copy shared by CSV and live market-screen sources. */
public record MarketScreenDataCopy(
        MarketScreenType screenType,
        String source,
        Instant observedAt,
        Instant fetchedAt,
        String freshness,
        List<MarketScreenObservation> observations,
        List<MarketScreenCsvParser.RowRejection> rejectedRows) {

    public MarketScreenDataCopy {
        Objects.requireNonNull(screenType, "Screen type is required");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Source is required");
        Objects.requireNonNull(observedAt, "Observation timestamp is required");
        Objects.requireNonNull(fetchedAt, "Fetch timestamp is required");
        if (freshness == null || freshness.isBlank()) throw new IllegalArgumentException("Freshness is required");
        observations = List.copyOf(observations);
        rejectedRows = List.copyOf(rejectedRows);
        if (observations.isEmpty()) throw new IllegalArgumentException("Market-screen data copy contains no usable rows");
        if (observations.stream().anyMatch(value -> value.screenType() != screenType))
            throw new IllegalArgumentException("Data copy cannot mix market-screen types");
    }
}
