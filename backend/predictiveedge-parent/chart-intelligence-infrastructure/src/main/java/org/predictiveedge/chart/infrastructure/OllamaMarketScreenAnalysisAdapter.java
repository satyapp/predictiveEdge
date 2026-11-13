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
import org.predictiveedge.chart.application.MarketScreenHistoricalAnalysisService;
import org.predictiveedge.chart.application.MarketScreenOllamaAnalysisPort;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.domain.IntradaySetupAction;

/** Local-model interpretation of deterministic Top Gainer/Loser historical evidence. */
public final class OllamaMarketScreenAnalysisAdapter implements MarketScreenOllamaAnalysisPort {
    private static final String SYSTEM = """
            You are PredictiveEdge's preliminary NSE market-screen analyst. Use only supplied deterministic facts.
            Never invent news, fundamentals, prices, indicators, probabilities, or events. Preserve every supplied
            action exactly: BUY_CANDIDATE=LONG_WATCH, SELL_CANDIDATE=SHORT_WATCH, WAIT=WAIT,
            INSUFFICIENT_EVIDENCE=INSUFFICIENT_EVIDENCE. Explain thesis, confirmation, and the kill condition.
            This is a watchlist assessment, never an executable order or final recommendation.
            """;
    private final ObjectMapper json; private final HttpClient http; private final URI endpoint;
    private final String model; private final Duration timeout; private final int contextTokens;
    private final int outputTokens; private final Clock clock;

    public OllamaMarketScreenAnalysisAdapter(ObjectMapper json, HttpClient http, URI endpoint, String model,
            Duration timeout, int contextTokens, int outputTokens, Clock clock) {
        this.json = Objects.requireNonNull(json); this.http = Objects.requireNonNull(http);
        this.endpoint = Objects.requireNonNull(endpoint); this.model = required(model, "Model");
        this.timeout = Objects.requireNonNull(timeout); this.contextTokens = contextTokens;
        this.outputTokens = outputTokens; this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PreOpenOllamaAnalysis analyze(MarketScreenHistoricalAnalysisService.Result result) {
        Map<String, String> expected = new LinkedHashMap<>();
        result.analyses().forEach(value -> expected.put(value.assessment().symbol(), stance(value.assessment().action())));
        if (expected.isEmpty()) return new PreOpenOllamaAnalysis(model, clock.instant(), false, "NO_SETUP",
                "No stock completed Zerodha historical analysis, so Ollama was not invoked.", List.of(),
                List.of("Resolve the per-stock historical failures before relying on this screen."));
        ObjectNode request = json.createObjectNode(); request.put("model", model).put("stream", false).put("think", false);
        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM);
        messages.addObject().put("role", "user").put("content", "Interpret this frozen evidence JSON:\n" + write(compact(result)));
        request.set("format", schema()); request.putObject("options").put("temperature", 0)
                .put("num_ctx", contextTokens).put("num_predict", outputTokens);
        try {
            var httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(write(request))).build();
            var response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
            JsonNode content = json.readTree(required(json.readTree(response.body()).path("message").path("content").asText(null), "Ollama content"));
            var candidates = new ArrayList<PreOpenOllamaAnalysis.CandidateInterpretation>();
            Map<String, String> returned = new LinkedHashMap<>();
            for (JsonNode value : content.path("candidates")) {
                var candidate = new PreOpenOllamaAnalysis.CandidateInterpretation(value.path("symbol").asText(),
                        value.path("stance").asText(), value.path("thesis").asText(),
                        value.path("confirmation").asText(), value.path("risk").asText());
                if (returned.put(candidate.symbol(), candidate.stance()) != null)
                    throw new IllegalStateException("Ollama duplicated candidate " + candidate.symbol());
                candidates.add(candidate);
            }
            if (!returned.equals(expected)) throw new IllegalStateException("Ollama changed deterministic symbols or directions");
            var cautions = new ArrayList<String>(); content.path("cautions").forEach(value -> cautions.add(value.asText()));
            return new PreOpenOllamaAnalysis(model, clock.instant(), true, content.path("market_tone").asText(),
                    content.path("overview").asText(), candidates, cautions);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Ollama analysis was interrupted", failure);
        } catch (IOException failure) { throw new IllegalStateException("Ollama analysis is unavailable", failure); }
    }

    private ObjectNode compact(MarketScreenHistoricalAnalysisService.Result result) {
        ObjectNode root = json.createObjectNode(); root.put("authority", result.authority());
        root.put("screen_type", result.screenType().name()); root.put("ranking_score_version", result.ranking().scoreVersion());
        ArrayNode values = root.putArray("deterministic_assessments");
        result.analyses().forEach(value -> {
            var assessment = value.assessment(); ObjectNode node = values.addObject();
            node.put("symbol", assessment.symbol()); node.put("action", assessment.action().name());
            node.put("percent_change", assessment.percentChange()); put(node, "ema20", assessment.ema20());
            put(node, "ema50", assessment.ema50()); put(node, "rsi14", assessment.rsi14());
            put(node, "atr14", assessment.atr14()); put(node, "reference_entry", assessment.referenceEntry());
            put(node, "indicative_stop", assessment.indicativeStop());
            node.put("confirmation_rule", assessment.confirmationRule());
            ArrayNode reasons = node.putArray("reasons"); assessment.reasons().forEach(reasons::add);
        });
        return root;
    }

    private ObjectNode schema() {
        ObjectNode schema = json.createObjectNode().put("type", "object").put("additionalProperties", false);
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("market_tone").put("type", "string").putArray("enum").add("BULLISH").add("BEARISH").add("MIXED").add("NO_SETUP");
        properties.putObject("overview").put("type", "string");
        ObjectNode item = properties.putObject("candidates").put("type", "array").putObject("items").put("type", "object").put("additionalProperties", false);
        ObjectNode fields = item.putObject("properties"); fields.putObject("symbol").put("type", "string");
        fields.putObject("stance").put("type", "string").putArray("enum").add("LONG_WATCH").add("SHORT_WATCH").add("WAIT").add("NO_TRADE").add("INSUFFICIENT_EVIDENCE");
        fields.putObject("thesis").put("type", "string"); fields.putObject("confirmation").put("type", "string"); fields.putObject("risk").put("type", "string");
        item.putArray("required").add("symbol").add("stance").add("thesis").add("confirmation").add("risk");
        properties.putObject("cautions").put("type", "array").putObject("items").put("type", "string");
        schema.putArray("required").add("market_tone").add("overview").add("candidates").add("cautions"); return schema;
    }
    private static String stance(IntradaySetupAction value) { return switch (value) {
        case BUY_CANDIDATE -> "LONG_WATCH"; case SELL_CANDIDATE -> "SHORT_WATCH"; case WAIT -> "WAIT";
        case NO_TRADE -> "NO_TRADE"; case INSUFFICIENT_EVIDENCE -> "INSUFFICIENT_EVIDENCE"; }; }
    private static void put(ObjectNode node, String name, java.math.BigDecimal value) { if (value == null) node.putNull(name); else node.put(name, value); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalArgumentException("Ollama payload cannot be serialized", failure); } }
    private static String required(String value, String label) { if (value == null || value.isBlank()) throw new IllegalStateException(label + " is missing"); return value.trim(); }
}
