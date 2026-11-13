package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.chart.application.PreOpenCsvAnalysisService;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysisPort;
import org.predictiveedge.chart.domain.IntradaySetupAction;

/** Local Ollama adapter for preliminary interpretation of governed pre-open candidates. */
public final class OllamaPreOpenAnalysisAdapter implements PreOpenOllamaAnalysisPort {
    private static final String SYSTEM = """
            You are PredictiveEdge's preliminary pre-open analyst. Interpret only the supplied deterministic facts.
            Never call an order final, never claim execution, and never invent a symbol, price, indicator or event.
            Preserve each supplied deterministic outcome exactly: BUY_CANDIDATE means LONG_WATCH,
            SELL_CANDIDATE means SHORT_WATCH, WAIT means WAIT, NO_TRADE means NO_TRADE, and
            INSUFFICIENT_EVIDENCE means INSUFFICIENT_EVIDENCE. Explain the evidence, the required confirmation,
            or the concrete reason that the stock is not currently actionable.
            Keep the overview under 100 words and each candidate field under 60 words.
            """;

    private final ObjectMapper json;
    private final HttpClient http;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;
    private final int contextTokens;
    private final int outputTokens;
    private final Clock clock;

    public OllamaPreOpenAnalysisAdapter(ObjectMapper json, HttpClient http, URI endpoint, String model,
            Duration timeout, int contextTokens, int outputTokens, Clock clock) {
        this.json = Objects.requireNonNull(json);
        this.http = Objects.requireNonNull(http);
        this.endpoint = Objects.requireNonNull(endpoint);
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Ollama model is required");
        this.model = model.trim();
        this.timeout = Objects.requireNonNull(timeout);
        if (contextTokens < 4096) throw new IllegalArgumentException("Ollama context must be at least 4096 tokens");
        if (outputTokens < 256 || outputTokens >= contextTokens)
            throw new IllegalArgumentException("Ollama output token allowance is invalid");
        this.contextTokens = contextTokens;
        this.outputTokens = outputTokens;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PreOpenOllamaAnalysis analyze(PreOpenCsvAnalysisService.Result result) {
        Objects.requireNonNull(result, "Deterministic result is required");
        Map<String, String> expected = expectedOutcomes(result);
        if (expected.isEmpty()) {
            String overview = result.failures().isEmpty()
                    ? "No stock completed deterministic historical analysis, so Qwen was not invoked."
                    : "Historical analysis failed for every shortlisted stock, so Qwen was not invoked.";
            return new PreOpenOllamaAnalysis(model, clock.instant(), false, "NO_SETUP", overview, List.of(),
                    List.of("Review the per-stock failures below and rerun after the data problem is resolved."));
        }
        ObjectNode request = json.createObjectNode();
        request.put("model", model).put("stream", false).put("think", false);
        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM);
        messages.addObject().put("role", "user").put("content",
                "Interpret this deterministic pre-open evidence JSON:\n" + write(compact(result)));
        request.set("format", schema());
        request.putObject("options").put("temperature", 0).put("num_ctx", contextTokens)
                .put("num_predict", outputTokens);
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(write(request))).build();
            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
            JsonNode content = json.readTree(required(json.readTree(response.body())
                    .path("message").path("content").asText(null), "Ollama content"));
            List<PreOpenOllamaAnalysis.CandidateInterpretation> candidates = new ArrayList<>();
            Map<String, String> returned = new LinkedHashMap<>();
            for (JsonNode value : content.path("candidates")) {
                var candidate = new PreOpenOllamaAnalysis.CandidateInterpretation(
                        value.path("symbol").asText(), value.path("stance").asText(),
                        value.path("thesis").asText(), value.path("confirmation").asText(),
                        value.path("risk").asText());
                if (returned.put(candidate.symbol(), candidate.stance()) != null)
                    throw new IllegalStateException("Ollama duplicated candidate " + candidate.symbol());
                candidates.add(candidate);
            }
            if (!returned.equals(expected))
                throw new IllegalStateException("Ollama candidate symbols or directions differ from deterministic evidence");
            List<String> cautions = new ArrayList<>();
            content.path("cautions").forEach(value -> cautions.add(value.asText()));
            return new PreOpenOllamaAnalysis(model, clock.instant(), true, content.path("market_tone").asText(),
                    content.path("overview").asText(), candidates, cautions);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama analysis was interrupted", failure);
        } catch (IOException failure) {
            throw new IllegalStateException("Ollama analysis is unavailable", failure);
        }
    }

    private ObjectNode compact(PreOpenCsvAnalysisService.Result result) {
        ObjectNode root = json.createObjectNode();
        root.put("authority", "PRELIMINARY_NOT_FINAL");
        root.put("parsed_snapshots", result.parsedSnapshots());
        root.put("shortlisted_for_history", result.shortlistedForHistory());
        root.put("ranking_score_version", result.ranking().scoreVersion());
        root.put("ranking_is_calibrated_probability", result.ranking().calibratedProbability());
        var ranks = new LinkedHashMap<String, org.predictiveedge.chart.domain.PreOpenCandidateRanker.RankedCandidate>();
        result.ranking().probableGainers().forEach(value -> ranks.put(value.symbol(), value));
        result.ranking().probableLosers().forEach(value -> ranks.put(value.symbol(), value));
        ArrayNode candidates = root.putArray("deterministic_assessments");
        result.analyses().stream().limit(15).forEach(value -> {
            var assessment = value.assessment();
            ObjectNode node = candidates.addObject();
            node.put("symbol", assessment.symbol());
            node.put("action", assessment.action().name());
            node.put("setup_type", assessment.setupType().name());
            node.put("gap_percent", assessment.gapPercent());
            put(node, "ema20", assessment.ema20()); put(node, "ema50", assessment.ema50());
            put(node, "rsi14", assessment.rsi14()); put(node, "atr14", assessment.atr14());
            put(node, "spread_bps", assessment.spreadBps());
            put(node, "auction_volume_ratio", assessment.auctionVolumeRatio());
            var rank = ranks.get(assessment.symbol());
            if (rank != null) {
                node.put("ranking_position", rank.rank());
                node.put("ranking_direction", rank.direction().name());
                node.put("ranking_score", rank.rankingScore());
                node.put("ranking_band", rank.scoreBand());
            }
            node.put("confirmation_rule", assessment.confirmationRule());
            ArrayNode reasons = node.putArray("reasons"); assessment.reasons().forEach(reasons::add);
        });
        return root;
    }

    private Map<String, String> expectedOutcomes(PreOpenCsvAnalysisService.Result result) {
        Map<String, String> expected = new LinkedHashMap<>();
        result.analyses().stream().limit(15).forEach(value -> expected.put(value.assessment().symbol(),
                stance(value.assessment().action())));
        return expected;
    }

    private static String stance(IntradaySetupAction action) {
        return switch (action) {
            case BUY_CANDIDATE -> "LONG_WATCH";
            case SELL_CANDIDATE -> "SHORT_WATCH";
            case WAIT -> "WAIT";
            case NO_TRADE -> "NO_TRADE";
            case INSUFFICIENT_EVIDENCE -> "INSUFFICIENT_EVIDENCE";
        };
    }

    private ObjectNode schema() {
        ObjectNode schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("market_tone").put("type", "string").putArray("enum")
                .add("BULLISH").add("BEARISH").add("MIXED").add("NO_SETUP");
        properties.putObject("overview").put("type", "string");
        ObjectNode item = properties.putObject("candidates").put("type", "array").putObject("items")
                .put("type", "object").put("additionalProperties", false);
        ObjectNode fields = item.putObject("properties");
        fields.putObject("symbol").put("type", "string");
        fields.putObject("stance").put("type", "string").putArray("enum")
                .add("LONG_WATCH").add("SHORT_WATCH").add("WAIT").add("NO_TRADE")
                .add("INSUFFICIENT_EVIDENCE");
        fields.putObject("thesis").put("type", "string");
        fields.putObject("confirmation").put("type", "string");
        fields.putObject("risk").put("type", "string");
        item.putArray("required").add("symbol").add("stance").add("thesis").add("confirmation").add("risk");
        properties.putObject("cautions").put("type", "array").putObject("items").put("type", "string");
        schema.putArray("required").add("market_tone").add("overview").add("candidates").add("cautions");
        return schema;
    }

    private static void put(ObjectNode node, String name, java.math.BigDecimal value) {
        if (value == null) node.putNull(name); else node.put(name, value);
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalArgumentException("Ollama payload could not be serialized", failure);
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is missing");
        return value;
    }
}
