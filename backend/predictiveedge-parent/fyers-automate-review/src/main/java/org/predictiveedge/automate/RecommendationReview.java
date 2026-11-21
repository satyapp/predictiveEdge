package org.predictiveedge.automate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One stock recommendation and every version, delivery and outcome recorded for it. */
public record RecommendationReview(
        UUID reviewId,
        UUID userId,
        String exchange,
        String symbol,
        String sourceRef,
        ReviewStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<RecommendationVersion> versions,
        List<WebhookDelivery> deliveries,
        TradeOutcome outcome) {

    public RecommendationReview {
        Objects.requireNonNull(reviewId, "Review id is required");
        Objects.requireNonNull(userId, "User id is required");
        exchange = normalizeExchange(exchange);
        symbol = normalizeSymbol(symbol);
        sourceRef = sourceRef == null || sourceRef.isBlank() ? "manual" : sourceRef.strip();
        Objects.requireNonNull(status, "Status is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
        Objects.requireNonNull(updatedAt, "Update time is required");
        versions = List.copyOf(versions == null ? List.of() : versions);
        deliveries = List.copyOf(deliveries == null ? List.of() : deliveries);
    }

    /** The newest version at a stage; a trader may correct a FIA review, and the newest wins. */
    public Optional<RecommendationVersion> latest(RecommendationStage stage) {
        return versions.stream().filter(v -> v.stage() == stage)
                .max(Comparator.comparing(RecommendationVersion::createdAt));
    }

    /** The recommendation currently in force: Guardian revision, else final, else FIA, else stage one. */
    public Optional<RecommendationVersion> current() {
        for (RecommendationStage stage : List.of(RecommendationStage.GUARDIAN_REVISION,
                RecommendationStage.FINAL_AI_FIA, RecommendationStage.FIA_REVIEW,
                RecommendationStage.PREDICTIVEEDGE_STAGE_ONE)) {
            Optional<RecommendationVersion> version = latest(stage);
            if (version.isPresent()) return version;
        }
        return Optional.empty();
    }

    /** FYERS symbol notation, for example NSE:SBIN-EQ. */
    public String fyersSymbol() {
        return symbol.contains(":") ? symbol : exchange + ":" + symbol + ("NSE".equals(exchange) || "BSE".equals(exchange) ? "-EQ" : "");
    }

    static String normalizeExchange(String exchange) {
        String value = exchange == null || exchange.isBlank() ? "NSE" : exchange.strip().toUpperCase(java.util.Locale.ROOT);
        if (!value.matches("[A-Z]{2,8}")) throw new IllegalArgumentException("Exchange is invalid");
        return value;
    }

    static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");
        String value = symbol.strip().toUpperCase(java.util.Locale.ROOT);
        if (value.endsWith("-EQ")) value = value.substring(0, value.length() - 3);
        if (value.contains(":")) value = value.substring(value.indexOf(':') + 1);
        if (!value.matches("[A-Z0-9&_.-]{1,40}")) throw new IllegalArgumentException("Symbol is invalid");
        return value;
    }
}
