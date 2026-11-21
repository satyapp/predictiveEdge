package org.predictiveedge.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore.StoredAnalysisRun;

/**
 * Builds one consolidated list of the stocks analysed by level-1 AI across every saved run (pre-open CSV,
 * market-screen CSV, live intraday screener and consolidated level one), plus any level-2 verdicts.
 */
public final class QualifiedStockService {
    public static final String LEVEL_TWO_KIND = "LEVEL_TWO_AI";
    static final List<String> DOMAINS = List.of("MARKET_DATA", "TECHNICAL_ANALYSIS", "CHART_PATTERNS",
            "FUNDAMENTAL_ANALYSIS", "NEWS_AND_SENTIMENT", "CORPORATE_ACTIONS", "MACRO_AND_ECONOMIC",
            "INSTITUTIONAL_FLOWS", "HISTORICAL_AND_PREDICTIVE");

    private final AnalysisRunHistoryStore runs;
    private final ObjectMapper json;

    public QualifiedStockService(AnalysisRunHistoryStore runs, ObjectMapper json) {
        this.runs = Objects.requireNonNull(runs);
        this.json = Objects.requireNonNull(json);
    }

    /** Latest level-1 observation per stock, newest first, with run count and latest level-2 verdict. */
    public List<QualifiedStock> qualifiedStocks(UUID userId, int runLimit) {
        Map<String, QualifiedStock> latest = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, LevelTwoVerdict> levelTwo = new HashMap<>();
        for (var summary : runs.findRecent(userId, Math.max(1, Math.min(runLimit, 200)))) {
            Optional<StoredAnalysisRun> run = runs.findById(userId, summary.runId());
            if (run.isEmpty()) continue;
            if (LEVEL_TWO_KIND.equals(run.get().resultKind())) {
                LevelTwoVerdict verdict = readVerdict(run.get());
                if (verdict != null) levelTwo.merge(verdict.symbol(), verdict, (a, b) -> a.createdAt().isAfter(b.createdAt()) ? a : b);
                continue;
            }
            for (QualifiedStock stock : flatten(run.get())) {
                counts.merge(stock.symbol(), 1, Integer::sum);
                latest.merge(stock.symbol(), stock, (a, b) -> a.analyzedAt().isAfter(b.analyzedAt()) ? a : b);
            }
        }
        return latest.values().stream()
                .map(s -> s.with(counts.getOrDefault(s.symbol(), 1), levelTwo.get(s.symbol())))
                .sorted(Comparator.comparing(QualifiedStock::analyzedAt).reversed()).toList();
    }

    /** The newest saved level-1 context for one stock, including its preserved evidence. */
    public Optional<LevelOneContext> levelOneContext(UUID userId, String symbol, UUID runId, int runLimit) {
        String wanted = normalize(symbol);
        List<UUID> candidates = runId != null ? List.of(runId)
                : runs.findRecent(userId, Math.max(1, Math.min(runLimit, 200))).stream().map(r -> r.runId()).toList();
        for (UUID id : candidates) {
            Optional<StoredAnalysisRun> run = runs.findById(userId, id);
            if (run.isEmpty() || LEVEL_TWO_KIND.equals(run.get().resultKind())) continue;
            for (QualifiedStock stock : flatten(run.get())) {
                if (stock.symbol().equals(wanted)) {
                    return Optional.of(new LevelOneContext(stock, run.get(), evidenceFor(read(run.get()), wanted)));
                }
            }
        }
        return Optional.empty();
    }

    List<QualifiedStock> flatten(StoredAnalysisRun run) {
        JsonNode root = read(run);
        if (root == null) return List.of();
        JsonNode aiCandidates = root.path("ollama").path("candidates");
        if (!aiCandidates.isArray() || aiCandidates.isEmpty()) return List.of();
        Map<String, JsonNode> assessments = new HashMap<>();
        for (String field : List.of("analyses", "candidates")) {
            for (JsonNode analysis : root.path("deterministic").path(field)) {
                JsonNode assessment = analysis.path("assessment");
                if (assessment.has("symbol")) assessments.putIfAbsent(normalize(assessment.path("symbol").asText()), analysis);
            }
        }
        Map<String, JsonNode> rankings = new HashMap<>();
        JsonNode ranking = root.path("deterministic").path("ranking");
        for (String field : List.of("candidates", "probableGainers", "probableLosers")) {
            for (JsonNode ranked : ranking.path(field)) rankings.putIfAbsent(normalize(ranked.path("symbol").asText()), ranked);
        }
        Map<String, Map<String, String>> evidence = evidenceCoverage(root);
        String model = root.path("ollama").path("model").asText(run.stageOneModel());
        Instant analyzedAt = run.createdAt();
        List<QualifiedStock> stocks = new ArrayList<>();
        for (JsonNode ai : aiCandidates) {
            String symbol = normalize(ai.path("symbol").asText());
            if (symbol.isEmpty()) continue;
            JsonNode analysis = assessments.get(symbol);
            JsonNode a = analysis == null ? json.missingNode() : analysis.path("assessment");
            JsonNode r = rankings.getOrDefault(symbol, json.missingNode());
            String stance = ai.path("stance").asText("INSUFFICIENT_EVIDENCE");
            stocks.add(new QualifiedStock(symbol, a.path("venue").asText("NSE"), run.runId(), run.resultKind(), run.category(),
                    run.tradingDate(), analyzedAt, stance, "LONG_WATCH".equals(stance) || "SHORT_WATCH".equals(stance),
                    model, ai.path("thesis").asText(""), ai.path("confirmation").asText(""), ai.path("risk").asText(""),
                    a.path("action").asText(null), a.path("confirmationRule").asText(null),
                    decimal(a, "referenceEntry"), decimal(a, "indicativeStop"), decimal(a, "indicativeTargetOne"),
                    decimal(a, "indicativeTargetTwo"), decimal(a, "rsi14"), decimal(a, "atr14"),
                    firstDecimal(r, a, "lastPrice"), firstDecimal(r, a, "percentChange", "gapPercent"),
                    r.has("rank") ? r.path("rank").asInt() : null,
                    firstDecimal(r, json.missingNode(), "score", "rankingScore"), r.path("scoreBand").asText(null),
                    evidence.getOrDefault(symbol, Map.of()), 1, null));
        }
        return stocks;
    }

    /** Per stock, per evidence domain: AVAILABLE when any provider returned SUCCESS, else MISSING. */
    Map<String, Map<String, String>> evidenceCoverage(JsonNode root) {
        JsonNode evidence = parseEvidence(root);
        Map<String, Map<String, String>> result = new HashMap<>();
        for (JsonNode stock : evidence.path("stocks")) {
            Map<String, String> domains = new LinkedHashMap<>();
            for (String domain : DOMAINS) {
                boolean available = false;
                for (JsonNode provider : stock.path("evidence_by_domain").path(domain)) {
                    if ("SUCCESS".equals(provider.path("status").asText())) { available = true; break; }
                }
                domains.put(domain, available ? "AVAILABLE" : "MISSING");
            }
            result.put(normalize(stock.path("symbol").asText()), domains);
        }
        return result;
    }

    JsonNode evidenceFor(JsonNode root, String symbol) {
        for (JsonNode stock : parseEvidence(root).path("stocks")) {
            if (normalize(stock.path("symbol").asText()).equals(symbol)) return stock;
        }
        return json.missingNode();
    }

    private JsonNode parseEvidence(JsonNode root) {
        if (root == null) return json.missingNode();
        JsonNode raw = root.path("goldenCopy").path("intelligenceEvidenceJson");
        if (raw.isObject()) return raw;
        if (!raw.isTextual()) return json.missingNode();
        try {
            return json.readTree(raw.asText());
        } catch (JsonProcessingException failure) {
            return json.missingNode();
        }
    }

    private LevelTwoVerdict readVerdict(StoredAnalysisRun run) {
        JsonNode root = read(run);
        if (root == null) return null;
        try {
            return json.treeToValue(root, LevelTwoVerdict.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            return null;
        }
    }

    private JsonNode read(StoredAnalysisRun run) {
        try {
            return json.readTree(run.resultJson());
        } catch (JsonProcessingException failure) {
            return null;
        }
    }

    static String normalize(String symbol) {
        if (symbol == null) return "";
        String value = symbol.strip().toUpperCase(Locale.ROOT);
        if (value.contains(":")) value = value.substring(value.indexOf(':') + 1);
        if (value.endsWith("-EQ")) value = value.substring(0, value.length() - 3);
        return value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.decimalValue() : null;
    }

    private static BigDecimal firstDecimal(JsonNode primary, JsonNode fallback, String... fields) {
        for (String field : fields) {
            BigDecimal value = decimal(primary, field);
            if (value != null) return value;
            value = decimal(fallback, field);
            if (value != null) return value;
        }
        return null;
    }

    public record LevelOneContext(QualifiedStock stock, StoredAnalysisRun run, JsonNode evidence) { }

    public record QualifiedStock(String symbol, String venue, UUID runId, String runKind, String category,
            LocalDate tradingDate, Instant analyzedAt, String stance, boolean qualified, String levelOneModel,
            String thesis, String confirmation, String risk, String setupAction, String confirmationRule,
            BigDecimal referenceEntry, BigDecimal indicativeStop, BigDecimal targetOne, BigDecimal targetTwo,
            BigDecimal rsi14, BigDecimal atr14, BigDecimal lastPrice, BigDecimal percentChange, Integer rank,
            BigDecimal score, String scoreBand, Map<String, String> evidence, int runCount, LevelTwoVerdict levelTwo) {
        QualifiedStock with(int runs, LevelTwoVerdict verdict) {
            return new QualifiedStock(symbol, venue, runId, runKind, category, tradingDate, analyzedAt, stance, qualified,
                    levelOneModel, thesis, confirmation, risk, setupAction, confirmationRule, referenceEntry, indicativeStop,
                    targetOne, targetTwo, rsi14, atr14, lastPrice, percentChange, rank, score, scoreBand, evidence, runs, verdict);
        }
    }
}
