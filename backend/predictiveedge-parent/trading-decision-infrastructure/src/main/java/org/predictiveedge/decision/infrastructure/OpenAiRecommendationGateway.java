package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.predictiveedge.decision.application.AiRecommendationGateway;
import org.predictiveedge.decision.application.AiEvidencePayloadQuery;
import org.predictiveedge.decision.domain.AIRecommendation;
import org.predictiveedge.decision.domain.AITradingDecisionInputBundle;
import org.predictiveedge.decision.domain.RecommendationAction;

/** OpenAI Responses API adapter with strict structured output; advisory shadow use only. */
public final class OpenAiRecommendationGateway implements AiRecommendationGateway {
    private static final String SYSTEM_PROMPT = """
            You are the advisory AI decision engine for a personal-use, single-equity shadow trading system.
            Never claim to execute or place an order. Evaluate only the supplied point-in-time evidence.
            Risk, Portfolio, Execution, Validation and data-quality constraints are mandatory and cannot be overridden.
            Return BUY or SELL only when the supplied facts are sufficient, mutually consistent, causal, and support
            positive expected value after costs. If payload references do not expose enough factual content, return WAIT.
            Do not invent prices, probabilities, evidence, news, portfolio facts, or risk limits.
            Keep the rationale concise (at most 120 words) and return only evidence references present in the input.
            """;

    private final ObjectMapper json;
    private final OpenAiResponsesTransport transport;
    private final String provider;
    private final String apiKey;
    private final String model;
    private final AiEvidencePayloadQuery evidencePayloadQuery;
    private final Clock clock;
    private final Supplier<String> recommendationIds;

    public OpenAiRecommendationGateway(ObjectMapper json, OpenAiResponsesTransport transport, String provider,
            String apiKey, String model, AiEvidencePayloadQuery evidencePayloadQuery, Clock clock,
            Supplier<String> recommendationIds) {
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        this.transport = Objects.requireNonNull(transport, "OpenAI transport is required");
        this.provider = required(provider, "AI provider").toLowerCase(java.util.Locale.ROOT);
        this.apiKey = required(apiKey, "OpenAI API key");
        this.model = required(model, "OpenAI model");
        this.evidencePayloadQuery = Objects.requireNonNull(evidencePayloadQuery, "AI evidence payload query is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        this.recommendationIds = Objects.requireNonNull(recommendationIds, "Recommendation id supplier is required");
    }

    @Override
    public AIRecommendation recommend(AITradingDecisionInputBundle bundle) {
        Objects.requireNonNull(bundle, "AI input bundle is required");
        if (!bundle.isReady()) throw new IllegalArgumentException("OpenAI cannot evaluate an unready input bundle");
        var completeInput = Objects.requireNonNull(evidencePayloadQuery.resolve(bundle),
                "AI evidence payload query returned no input");
        if (!completeInput.bundle().equals(bundle)) {
            throw new IllegalArgumentException("Resolved AI evidence does not match the decision bundle");
        }
        String raw = transport.createResponse(apiKey, write(request(completeInput)));
        JsonNode output = parseStructuredOutput(raw);
        Instant generatedAt = clock.instant();
        RecommendationAction action = RecommendationAction.valueOf(requiredText(output, "action"));
        BigDecimal probability = output.path("calibrated_win_probability").decimalValue();
        BigDecimal expectedValue = output.path("expected_value_after_costs").decimalValue();
        List<String> evidence = new ArrayList<>();
        output.path("evidence_references").forEach(value -> evidence.add(value.asText()));

        if (!action.isDirectional()) {
            return new AIRecommendation(required(recommendationIds.get(), "Recommendation id"), bundle.bundleId(),
                    bundle.scope(), action, generatedAt, probability, expectedValue, null, null, null, null,
                    null, null, auditedModel(), requiredText(output, "rationale"), evidence);
        }

        long entrySeconds = output.path("entry_valid_for_seconds").asLong();
        long horizonSeconds = output.path("evaluation_horizon_seconds").asLong();
        Instant entryValidUntil = generatedAt.plusSeconds(entrySeconds);
        if (generatedAt.isBefore(bundle.executionContext().validUntil())
                && entryValidUntil.isAfter(bundle.executionContext().validUntil())) {
            entryValidUntil = bundle.executionContext().validUntil();
        }
        if (!generatedAt.isBefore(entryValidUntil)) entryValidUntil = generatedAt.plusSeconds(1);
        Instant evaluationHorizon = generatedAt.plusSeconds(horizonSeconds);
        if (!entryValidUntil.isBefore(evaluationHorizon)) evaluationHorizon = entryValidUntil.plusSeconds(1);
        return new AIRecommendation(required(recommendationIds.get(), "Recommendation id"), bundle.bundleId(),
                bundle.scope(), action, generatedAt, probability, expectedValue,
                decimal(output, "entry_price_low"), decimal(output, "entry_price_high"), entryValidUntil,
                evaluationHorizon, decimal(output, "stop_loss"), decimal(output, "target"), auditedModel(),
                requiredText(output, "rationale"), evidence);
    }

    private ObjectNode request(org.predictiveedge.decision.application.AiModelEvidenceInput completeInput) {
        ObjectNode request = json.createObjectNode();
        request.put("model", model);
        ArrayNode input = request.putArray("input");
        message(input, "system", SYSTEM_PROMPT);
        message(input, "user", "Evaluate this complete immutable shadow evidence input:\n" + writeAiInput(completeInput));
        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "shadow_trade_recommendation");
        format.put("strict", true);
        format.set("schema", schema());
        return request;
    }

    private void message(ArrayNode input, String role, String text) {
        ObjectNode message = input.addObject();
        message.put("role", role);
        message.putArray("content").addObject().put("type", "input_text").put("text", text);
    }

    private ObjectNode schema() {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("action").put("type", "string").putArray("enum").add("BUY").add("SELL").add("WAIT");
        number(properties, "calibrated_win_probability", 0, 1);
        properties.putObject("expected_value_after_costs").put("type", "number");
        nullableNumber(properties, "entry_price_low");
        nullableNumber(properties, "entry_price_high");
        nullableInteger(properties, "entry_valid_for_seconds");
        nullableInteger(properties, "evaluation_horizon_seconds");
        nullableNumber(properties, "stop_loss");
        nullableNumber(properties, "target");
        properties.putObject("rationale").put("type", "string");
        properties.putObject("evidence_references").put("type", "array")
                .set("items", json.createObjectNode().put("type", "string"));
        schema.putArray("required").add("action").add("calibrated_win_probability")
                .add("expected_value_after_costs").add("entry_price_low").add("entry_price_high")
                .add("entry_valid_for_seconds").add("evaluation_horizon_seconds").add("stop_loss").add("target")
                .add("rationale").add("evidence_references");
        return schema;
    }

    private void number(ObjectNode properties, String name, int minimum, int maximum) {
        properties.putObject(name).put("type", "number").put("minimum", minimum).put("maximum", maximum);
    }

    private void nullableNumber(ObjectNode properties, String name) {
        properties.putObject(name).putArray("type").add("number").add("null");
    }

    private void nullableInteger(ObjectNode properties, String name) {
        properties.putObject(name).putArray("type").add("integer").add("null");
    }

    private JsonNode parseStructuredOutput(String raw) {
        try {
            JsonNode response = json.readTree(raw);
            for (JsonNode item : response.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode content : item.path("content")) {
                    if ("refusal".equals(content.path("type").asText())) {
                        throw new IllegalStateException("OpenAI refused the shadow recommendation request");
                    }
                    if ("output_text".equals(content.path("type").asText())) {
                        return json.readTree(content.path("text").asText());
                    }
                }
            }
            throw new IllegalStateException("OpenAI response contains no structured output");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("OpenAI returned invalid structured output", exception);
        }
    }

    private BigDecimal decimal(JsonNode output, String field) {
        JsonNode value = output.path(field);
        if (!value.isNumber()) throw new IllegalStateException("OpenAI omitted directional field " + field);
        return value.decimalValue();
    }

    private static String requiredText(JsonNode node, String field) {
        return required(node.path(field).asText(null), "OpenAI field " + field);
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("OpenAI request payload cannot be serialized", exception);
        }
    }

    private String writeAiInput(org.predictiveedge.decision.application.AiModelEvidenceInput input) {
        ObjectNode root = json.createObjectNode();
        root.set("bundle", json.valueToTree(input.bundle()));
        ObjectNode payloads = root.putObject("resource_payloads");
        input.resourcePayloadJson().forEach((type, payload) -> {
            try {
                payloads.set(type.name(), json.readTree(payload));
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Resolved " + type + " payload is not valid JSON", exception);
            }
        });
        return write(root);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private String auditedModel() {
        return provider + ":" + model;
    }
}
