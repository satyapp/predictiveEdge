package org.predictiveedge.automate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Objects;

/**
 * Builds the flat JSON body posted to FYERS Automate. It is flat so Automate workflow steps can read each
 * field directly. Every payload carries review_only=true and live_order=false, and deliberately omits keys such
 * as "lots" that community Automate examples map straight into order blocks.
 */
public final class AutomatePayloads {
    private final ObjectMapper json;
    private final String webBaseUrl;

    public AutomatePayloads(ObjectMapper json, String webBaseUrl) {
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.webBaseUrl = webBaseUrl == null ? "" : webBaseUrl.replaceAll("/+$", "");
    }

    public String recommendation(RecommendationReview review, RecommendationVersion version, Instant sentAt) {
        ObjectNode body = base("RECOMMENDATION", sentAt);
        body.put("stage", version.stage().name());
        body.put("review_id", review.reviewId().toString());
        body.put("symbol", review.fyersSymbol());
        body.put("exchange", review.exchange());
        body.put("ticker", review.symbol());
        RecommendationDraft d = version.draft();
        body.put("action", d.action().name());
        body.put("entry_low", d.entryLow());
        body.put("entry_high", d.entryHigh());
        body.put("stop_loss", d.stopLoss());
        body.put("target_one", d.targetOne());
        body.put("target_two", d.targetTwo());
        if (d.quantity() == null) body.putNull("quantity"); else body.put("quantity", d.quantity());
        if (d.confidence() == null) body.putNull("confidence"); else body.put("confidence", d.confidence());
        body.put("rationale", d.rationale().length() > 1000 ? d.rationale().substring(0, 997) + "..." : d.rationale());
        body.put("recommendation_source", version.source());
        body.put("generated_at", version.createdAt().toString());
        if (!webBaseUrl.isEmpty()) body.put("review_url", webBaseUrl + "/?automateReview=" + review.reviewId());
        return write(body);
    }

    public String test(Instant sentAt) {
        ObjectNode body = base("TEST", sentAt);
        body.put("message", "PredictiveEdge webhook test. No recommendation and no order.");
        return write(body);
    }

    private ObjectNode base(String type, Instant sentAt) {
        ObjectNode body = json.createObjectNode();
        body.put("source", "PredictiveEdge");
        body.put("message_type", type);
        body.put("review_only", true);
        body.put("live_order", false);
        body.put("instruction", "Review with FIA. PredictiveEdge does not place orders; confirm any order yourself in FYERS.");
        body.put("sent_at", sentAt.toString());
        return body;
    }

    private String write(ObjectNode body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Automate payload cannot be serialized", exception);
        }
    }
}
