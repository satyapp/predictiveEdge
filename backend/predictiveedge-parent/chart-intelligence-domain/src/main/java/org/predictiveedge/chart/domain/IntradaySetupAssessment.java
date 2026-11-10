package org.predictiveedge.chart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Explainable pre-open candidate assessment with indicative, confirmation-gated levels. */
public record IntradaySetupAssessment(
        String venue,
        String symbol,
        IntradaySetupAction action,
        IntradaySetupType setupType,
        String confirmationRule,
        BigDecimal gapPercent,
        BigDecimal ema20,
        BigDecimal ema50,
        BigDecimal rsi14,
        BigDecimal atr14,
        BigDecimal spreadBps,
        BigDecimal auctionTurnover,
        BigDecimal auctionVolumeRatio,
        BigDecimal referenceEntry,
        BigDecimal indicativeStop,
        BigDecimal indicativeTargetOne,
        BigDecimal indicativeTargetTwo,
        Instant analysisCutoff,
        List<String> reasons,
        List<String> evidenceReferences) {

    public IntradaySetupAssessment {
        if (venue == null || venue.isBlank()) throw new IllegalArgumentException("Venue is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
        venue = venue.trim().toUpperCase(); symbol = symbol.trim().toUpperCase();
        Objects.requireNonNull(action, "Action is required");
        Objects.requireNonNull(setupType, "Setup type is required");
        if (confirmationRule == null || confirmationRule.isBlank())
            throw new IllegalArgumentException("Confirmation rule is required");
        confirmationRule = confirmationRule.trim();
        Objects.requireNonNull(gapPercent, "Gap percent is required");
        Objects.requireNonNull(spreadBps, "Spread is required");
        Objects.requireNonNull(auctionTurnover, "Auction turnover is required");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "Reasons are required"));
        evidenceReferences = List.copyOf(Objects.requireNonNull(evidenceReferences, "Evidence references are required"));
    }
}
