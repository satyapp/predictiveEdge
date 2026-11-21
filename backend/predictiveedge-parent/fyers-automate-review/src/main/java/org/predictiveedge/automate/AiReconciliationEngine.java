package org.predictiveedge.automate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Objects;
import org.predictiveedge.decision.infrastructure.OpenAiResponsesTransport;

/**
 * Reconciles PredictiveEdge stage one with the FIA review through the configured AI provider (OpenAI Responses
 * API or local Ollama), with strict structured output. Output is validated; if the model fails, returns invalid
 * levels, or picks a direction neither engine proposed, the deterministic rules are used instead.
 */
public final class AiReconciliationEngine implements ReconciliationEngine {
    static final String SYSTEM_PROMPT = """
            You reconcile two independent equity trade recommendations for the same stock into one final advisory
            recommendation for a human trader. Recommendation A comes from PredictiveEdge. Recommendation B is
            FIA's (the FYERS Intelligent Assistant) review and correction of A, entered by the trader.
            Never claim to place, modify or cancel an order: the trader confirms any order manually in FYERS.
            Weigh each side's reasoning and the supplied realized track record. Prefer the more conservative
            levels when uncertain. If A and B point in opposite directions and the evidence does not clearly
            favour one, return WAIT. Never return a direction that neither A nor B proposed.
            For BUY the stop must be below the entry and targets above it; for SELL the reverse.
            Use null for levels that do not apply. Keep the rationale under 150 words and state where you
            agreed with A, where with B, and why.
            """;

    private final ObjectMapper json;
    private final OpenAiResponsesTransport transport;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final DeterministicReconciliationEngine fallback = new DeterministicReconciliationEngine();

    public AiReconciliationEngine(ObjectMapper json, OpenAiResponsesTransport transport, String provider,
            String apiKey, String model) {
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.transport = Objects.requireNonNull(transport, "AI transport is required");
        this.provider = Objects.requireNonNull(provider, "Provider is required");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = Objects.requireNonNull(model, "Model is required");
    }

    @Override public String name() { return provider + ":" + model; }

    @Override
    public Result reconcile(Input input) {
        try {
            String raw = transport.createResponse(apiKey, json.writeValueAsString(request(input)));
            RecommendationDraft draft = parse(extract(raw));
            TradeAction a = input.stageOne().draft().action();
            TradeAction b = input.fiaReview().draft().action();
            if (draft.action().isDirectional() && draft.action() != a && draft.action() != b) {
                throw new IllegalStateException("model chose " + draft.action() + ", which neither engine proposed");
            }
            return new Result(draft, name());
        } catch (RuntimeException | JsonProcessingException failure) {
            Result rules = fallback.reconcile(input);
            RecommendationDraft d = rules.draft();
            String note = " (AI reconciliation via " + name() + " was unavailable: " + failure.getMessage()
                    + "; PredictiveEdge rules were applied.)";
            return new Result(new RecommendationDraft(d.action(), d.entryLow(), d.entryHigh(), d.stopLoss(),
                    d.targetOne(), d.targetTwo(), d.quantity(), d.confidence(), d.rationale() + note), rules.engine());
        }
    }

    ObjectNode request(Input input) {
        ObjectNode request = json.createObjectNode();
        request.put("model", model);
        ArrayNode messages = request.putArray("input");
        message(messages, "system", SYSTEM_PROMPT);
        ObjectNode facts = json.createObjectNode();
        facts.put("exchange", input.exchange());
        facts.put("symbol", input.symbol());
        facts.set("recommendation_a_predictiveedge", draftJson(input.stageOne().draft()));
        facts.set("recommendation_b_fia_review", draftJson(input.fiaReview().draft()));
        facts.set("realized_track_record", json.valueToTree(input.learning()));
        ArrayNode recent = facts.putArray("recent_closed_trades_same_stock");
        input.pastReviews().stream().filter(r -> r.outcome() != null).limit(5).forEach(r -> {
            ObjectNode item = recent.addObject();
            r.latest(RecommendationStage.PREDICTIVEEDGE_STAGE_ONE).ifPresent(v -> item.put("predictiveedge_action", v.draft().action().name()));
            r.latest(RecommendationStage.FIA_REVIEW).ifPresent(v -> item.put("fia_action", v.draft().action().name()));
            r.latest(RecommendationStage.FINAL_AI_FIA).ifPresent(v -> item.put("final_action", v.draft().action().name()));
            item.put("traded_direction", r.outcome().direction().name());
            item.put("followed_stage", r.outcome().followedStage().name());
            item.put("realized_pnl", r.outcome().realizedPnl());
            item.put("return_percent", r.outcome().returnPercent());
        });
        try {
            message(messages, "user", "Reconcile these recommendations:\n" + json.writeValueAsString(facts));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Reconciliation input cannot be serialized", exception);
        }
        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "final_trade_recommendation");
        format.put("strict", true);
        format.set("schema", schema());
        return request;
    }

    private ObjectNode draftJson(RecommendationDraft draft) {
        ObjectNode node = json.createObjectNode();
        node.put("action", draft.action().name());
        node.put("entry_low", draft.entryLow());
        node.put("entry_high", draft.entryHigh());
        node.put("stop_loss", draft.stopLoss());
        node.put("target_one", draft.targetOne());
        node.put("target_two", draft.targetTwo());
        if (draft.quantity() == null) node.putNull("quantity"); else node.put("quantity", draft.quantity());
        if (draft.confidence() == null) node.putNull("confidence"); else node.put("confidence", draft.confidence());
        node.put("reasoning", draft.rationale());
        return node;
    }

    private ObjectNode schema() {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode p = schema.putObject("properties");
        p.putObject("action").put("type", "string").putArray("enum").add("BUY").add("SELL").add("WAIT").add("NO_TRADE");
        for (String name : new String[] {"entry_low", "entry_high", "stop_loss", "target_one", "target_two"}) {
            p.putObject(name).putArray("type").add("number").add("null");
        }
        p.putObject("quantity").putArray("type").add("integer").add("null");
        p.putObject("confidence").put("type", "integer").put("minimum", 0).put("maximum", 100);
        p.putObject("rationale").put("type", "string");
        ArrayNode required = schema.putArray("required");
        p.fieldNames().forEachRemaining(required::add);
        return schema;
    }

    private void message(ArrayNode input, String role, String text) {
        ObjectNode message = input.addObject();
        message.put("role", role);
        message.putArray("content").addObject().put("type", "input_text").put("text", text);
    }

    private JsonNode extract(String raw) throws JsonProcessingException {
        JsonNode response = json.readTree(raw);
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asText())) continue;
            for (JsonNode content : item.path("content")) {
                if ("refusal".equals(content.path("type").asText())) throw new IllegalStateException("model refused");
                if ("output_text".equals(content.path("type").asText())) return json.readTree(content.path("text").asText());
            }
        }
        throw new IllegalStateException("response has no structured output");
    }

    private RecommendationDraft parse(JsonNode o) {
        TradeAction action = TradeAction.valueOf(o.path("action").asText());
        boolean directional = action.isDirectional();
        return new RecommendationDraft(action,
                directional ? decimal(o, "entry_low") : null, directional ? decimal(o, "entry_high") : null,
                directional ? decimal(o, "stop_loss") : null, directional ? decimal(o, "target_one") : null,
                directional ? decimal(o, "target_two") : null,
                directional && o.path("quantity").canConvertToInt() && o.path("quantity").asInt() > 0 ? o.path("quantity").asInt() : null,
                o.path("confidence").asInt(), o.path("rationale").asText(""));
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }
}
