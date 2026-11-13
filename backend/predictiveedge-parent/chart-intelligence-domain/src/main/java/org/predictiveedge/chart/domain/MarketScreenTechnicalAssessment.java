package org.predictiveedge.chart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Historical/technical assessment of one ranked market-screen observation. */
public record MarketScreenTechnicalAssessment(
        String venue,
        String symbol,
        IntradaySetupAction action,
        String confirmationRule,
        BigDecimal percentChange,
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal rsi14,
        BigDecimal atr14,
        long screenVolume,
        BigDecimal screenTurnover,
        BigDecimal referenceEntry,
        BigDecimal indicativeStop,
        BigDecimal indicativeTargetOne,
        BigDecimal indicativeTargetTwo,
        Instant analysisCutoff,
        List<String> reasons,
        List<String> evidenceReferences) {

    public MarketScreenTechnicalAssessment {
        venue = required(venue, "Venue").toUpperCase(java.util.Locale.ROOT);
        symbol = required(symbol, "Symbol").toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(action, "Action is required");
        confirmationRule = required(confirmationRule, "Confirmation rule");
        Objects.requireNonNull(percentChange, "Percent change is required");
        if (screenVolume < 0) throw new IllegalArgumentException("Screen volume cannot be negative");
        Objects.requireNonNull(screenTurnover, "Screen turnover is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "Reasons are required"));
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "Evidence references are required"));
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
