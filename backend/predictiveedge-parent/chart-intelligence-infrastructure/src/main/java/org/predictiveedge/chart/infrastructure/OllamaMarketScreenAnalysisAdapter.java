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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.chart.application.MarketScreenHistoricalAnalysisService;
import org.predictiveedge.chart.application.MarketScreenOllamaAnalysisPort;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.ScreenerRunRecorder;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneAiResult;
import org.predictiveedge.chart.domain.IntradaySetupAction;

/** Local-model interpretation of deterministic Top Gainer/Loser historical evidence. */
public final class OllamaMarketScreenAnalysisAdapter implements MarketScreenOllamaAnalysisPort {
    private static final String SYSTEM = """
            You are PredictiveEdge's preliminary NSE market-screen analyst. Use only supplied deterministic facts.
            Never invent news, fundamentals, prices, indicators, probabilities, or events. Return every supplied
            symbol exactly once and preserve its required_stance exactly. Explain thesis, confirmation, and the kill condition.
            Evaluate Market, Chart, Scanner, Strategy, Risk, Portfolio, Learning, Data Quality, Regime Drift,
            Execution, Validation and Calibration. Respect provider status; unavailable data is not negative data.
            Trendlyne technical and fundamental/SWOT evidence is supplementary. Call out conflicts and never use
            supplementary context to change a deterministic action.
            This is a watchlist assessment, never an executable order or final recommendation.
            """;
    private final ObjectMapper json; private final HttpClient http; private final URI endpoint;
    private final String model; private final Duration timeout; private final int contextTokens;
    private final int outputTokens; private final Clock clock;
    private final StageOneIntelligenceEvidenceCollector intelligence;
    private final StageOneGoldenCopyFactory goldenCopies;
    private final StageOneActivityStore store;

    /** {@code store} may be null, in which case no run is saved even when the caller names one. */
    public OllamaMarketScreenAnalysisAdapter(ObjectMapper json, HttpClient http, URI endpoint, String model,
            Duration timeout, int contextTokens, int outputTokens, Clock clock,
            StageOneIntelligenceEvidenceCollector intelligence, StageOneGoldenCopyFactory goldenCopies,
            StageOneActivityStore store) {
        this.json = Objects.requireNonNull(json); this.http = Objects.requireNonNull(http);
        this.endpoint = Objects.requireNonNull(endpoint); this.model = required(model, "Model");
        this.timeout = Objects.requireNonNull(timeout); this.contextTokens = contextTokens;
        this.outputTokens = outputTokens; this.clock = Objects.requireNonNull(clock);
        this.intelligence = Objects.requireNonNull(intelligence);
        this.goldenCopies = Objects.requireNonNull(goldenCopies);
        this.store = store;
    }

    /**
     * Fetches evidence for every candidate in parallel, collates each stock from its stored evidence, then asks the
     * model. With a run, the candidates are saved as selections of that run before fetching, and each candidate's
     * analysis is saved after the model answers.
     */
    @Override
    public StageOneAiResult analyze(UUID userId, String brokerAccountId,
            MarketScreenHistoricalAnalysisService.Result result, ScreenerRunRecorder.ScreenerRun run) {
        BrokerContext brokerContext = BrokerContext.withoutCredentials(userId, brokerAccountId);
        StageOneStoreEvidenceWriter saved = run == null || store == null ? null
                : new StageOneStoreEvidenceWriter(store, json, run.searchId(), userId, run.category(),
                        clock.instant());
        EvidenceStage stage = saved == null ? new InMemoryEvidenceStage() : saved;
        ObjectNode evidence = intelligence.collect(
                result.ranking().candidates().stream().map(value -> value.symbol()).toList(),
                result.analyzedAt(), brokerContext, stage);
        String evidenceJson = write(compact(result, evidence));
        Map<String, String> expected = new LinkedHashMap<>();
        result.ranking().candidates().forEach(value -> expected.put(value.symbol(), "INSUFFICIENT_EVIDENCE"));
        result.analyses().forEach(value -> expected.put(value.assessment().symbol(), stance(value.assessment().action())));
        if (expected.isEmpty()) {
            var analysis = new PreOpenOllamaAnalysis(model, clock.instant(), false, "NO_SETUP",
                    "The broker screen contained no candidates, so Ollama was not invoked.", List.of(),
                    List.of("Run the broker screen again before requesting AI analysis."));
            return goldenCopies.freeze(result.screenType().name(), evidenceJson, analysis);
        }
        ObjectNode request = json.createObjectNode(); request.put("model", model).put("stream", false).put("think", false);
        ArrayNode messages = request.putArray("messages");
        messages.addObject().put("role", "system").put("content", SYSTEM);
        messages.addObject().put("role", "user").put("content",
                "Evaluate all twelve intelligence modules in this frozen evidence JSON:\n" + evidenceJson);
        request.set("format", schema()); request.putObject("options").put("temperature", 0)
                .put("num_ctx", contextTokens).put("num_predict", outputTokens);
        try {
            var httpRequest = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(write(request))).build();
            var response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode());
            JsonNode content = json.readTree(required(json.readTree(response.body()).path("message").path("content").asText(null), "Ollama content"));
            var cautions = new ArrayList<String>(); content.path("cautions").forEach(value -> cautions.add(value.asText()));
            var candidates = reconcileCandidates(content, expected, cautions);
            var analysis = new PreOpenOllamaAnalysis(model, clock.instant(), true,
                    content.path("market_tone").asText(), content.path("overview").asText(), candidates, cautions);
            if (saved != null) recordAnalyses(saved, analysis);
            return goldenCopies.freeze(result.screenType().name(), evidenceJson, analysis);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("Ollama analysis was interrupted", failure);
        } catch (IOException failure) { throw new IllegalStateException("Ollama analysis is unavailable", failure); }
    }

    /** Saves each candidate's interpretation against its selection. Candidates without a selection are skipped. */
    private void recordAnalyses(StageOneStoreEvidenceWriter saved, PreOpenOllamaAnalysis analysis) {
        String analysisJson = write(analysis);
        Map<String, UUID> selections = saved.selectionIds();
        for (PreOpenOllamaAnalysis.CandidateInterpretation candidate : analysis.candidates()) {
            UUID selectionId = selections.get(candidate.symbol());
            if (selectionId == null) continue;
            store.recordAnalysis(selectionId, new StageOneActivityStore.StageOneAnalysisInput(candidate.stance(),
                    candidate.thesis(), candidate.confirmation(), candidate.risk(), analysis.model(),
                    analysis.generatedAt(), analysisJson));
        }
    }

    /** Keeps deterministic identity and direction authoritative without discarding useful model explanations. */
    static List<PreOpenOllamaAnalysis.CandidateInterpretation> reconcileCandidates(
            JsonNode content, Map<String, String> expected, List<String> cautions) {
        Map<String, PreOpenOllamaAnalysis.CandidateInterpretation> accepted = new LinkedHashMap<>();
        for (JsonNode value : content.path("candidates")) {
            String rawSymbol = value.path("symbol").asText();
            String symbol = normalizeSymbol(rawSymbol);
            if (!expected.containsKey(symbol)) {
                cautions.add("Ollama returned unknown symbol " + rawSymbol + "; it was ignored.");
                continue;
            }
            if (accepted.containsKey(symbol)) {
                cautions.add("Ollama duplicated " + symbol + "; only the first explanation was retained.");
                continue;
            }
            String deterministicStance = expected.get(symbol);
            String proposedStance = value.path("stance").asText().trim().toUpperCase(Locale.ROOT);
            if (!deterministicStance.equals(proposedStance)) {
                cautions.add("Ollama proposed " + proposedStance + " for " + symbol
                        + "; deterministic stance " + deterministicStance + " was retained.");
            }
            accepted.put(symbol, new PreOpenOllamaAnalysis.CandidateInterpretation(symbol,
                    deterministicStance, value.path("thesis").asText(),
                    value.path("confirmation").asText(), value.path("risk").asText()));
        }
        var reconciled = new ArrayList<PreOpenOllamaAnalysis.CandidateInterpretation>();
        expected.forEach((symbol, stance) -> {
            var candidate = accepted.get(symbol);
            if (candidate != null) {
                reconciled.add(candidate);
                return;
            }
            cautions.add("Ollama omitted " + symbol + "; a deterministic-only row was retained.");
            reconciled.add(new PreOpenOllamaAnalysis.CandidateInterpretation(symbol, stance,
                    "Ollama returned no explanation for this stock; only deterministic screening evidence is available.",
                    "Review the deterministic confirmation rule before taking any action.",
                    "AI interpretation is missing; treat this row as incomplete evidence."));
        });
        return List.copyOf(reconciled);
    }

    private static String normalizeSymbol(String value) {
        String symbol = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (symbol.startsWith("NSE:")) symbol = symbol.substring(4);
        if (symbol.endsWith("-EQ")) symbol = symbol.substring(0, symbol.length() - 3);
        return symbol;
    }

    private ObjectNode compact(MarketScreenHistoricalAnalysisService.Result result, ObjectNode evidence) {
        ObjectNode root = json.createObjectNode(); root.put("authority", result.authority());
        root.put("screen_type", result.screenType().name()); root.put("ranking_score_version", result.ranking().scoreVersion());
        Map<String, org.predictiveedge.chart.domain.MarketScreenTechnicalAssessment> assessments = result.analyses()
                .stream().collect(java.util.stream.Collectors.toMap(value -> value.assessment().symbol(),
                        MarketScreenHistoricalAnalysisService.InstrumentAnalysis::assessment));
        Map<String, String> failures = result.failures().stream().collect(java.util.stream.Collectors.toMap(
                MarketScreenHistoricalAnalysisService.InstrumentFailure::symbol,
                MarketScreenHistoricalAnalysisService.InstrumentFailure::reason, (left, right) -> left));
        ArrayNode brokerCandidates = root.putArray("broker_screen_candidates");
        result.ranking().candidates().forEach(value -> {
            ObjectNode node = brokerCandidates.addObject();
            node.put("symbol", value.symbol()); node.put("screen_rank", value.rank());
            node.put("screen_score", value.score()); node.put("percent_change", value.percentChange());
            var assessment = assessments.get(value.symbol());
            node.put("required_stance", assessment == null
                    ? "INSUFFICIENT_EVIDENCE" : stance(assessment.action()));
            if (assessment == null) node.put("historical_data_status", failures.getOrDefault(value.symbol(),
                    "Historical analysis was unavailable"));
        });
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
        root.set("intelligence_evidence", evidence);
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
