package org.predictiveedge.decision.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.domain.Instrument;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.RecommendationAction;

/** Explicit trader-scoped proposal assessed by safety producers; it is not an order request. */
public record SafetyAssessmentRequest(
        UUID userId,
        String brokerAccountId,
        InstrumentRef instrument,
        Instrument marketInstrument,
        RecommendationAction direction,
        int quantity,
        BigDecimal stopLossPrice,
        Instant analysisCutoff,
        Instant knowledgeCutoff) {
    public SafetyAssessmentRequest {
        Objects.requireNonNull(userId, "User id is required");
        if (brokerAccountId == null || brokerAccountId.isBlank())
            throw new IllegalArgumentException("Broker account id is required");
        Objects.requireNonNull(instrument, "Decision instrument is required");
        Objects.requireNonNull(marketInstrument, "Market instrument is required");
        Objects.requireNonNull(direction, "Direction is required");
        if (!direction.isDirectional()) throw new IllegalArgumentException("Direction must be BUY or SELL");
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
        Objects.requireNonNull(stopLossPrice, "Stop-loss price is required");
        if (stopLossPrice.signum() <= 0) throw new IllegalArgumentException("Stop-loss price must be positive");
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
        if (analysisCutoff.isAfter(knowledgeCutoff))
            throw new IllegalArgumentException("Analysis cutoff cannot follow knowledge cutoff");
    }
}
