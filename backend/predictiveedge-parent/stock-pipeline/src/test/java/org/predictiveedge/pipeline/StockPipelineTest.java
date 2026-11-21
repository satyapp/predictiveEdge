package org.predictiveedge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;

class StockPipelineTest {
    private static final UUID USER = UUID.randomUUID();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final MemoryRuns runs = new MemoryRuns();
    private final QualifiedStockService service = new QualifiedStockService(runs, json);

    @Test
    void consolidatesLatestLevelOnePerStockAcrossRuns() throws Exception {
        runs.add("MARKET_SCREEN_FULL", "TOP_GAINERS", Instant.parse("2026-11-20T05:00:00Z"), marketScreen("SBIN", "LONG_WATCH", 800));
        runs.add("CONSOLIDATED_LEVEL_ONE", "INTRADAY_OPPORTUNITY", Instant.parse("2026-11-21T05:00:00Z"), marketScreen("SBIN", "LONG_WATCH", 812));
        runs.add("MARKET_SCREEN_FULL", "TOP_LOSERS", Instant.parse("2026-11-21T06:00:00Z"), marketScreen("INFY", "WAIT", 1500));
        runs.add("MARKET_SCREEN", "TOP_LOSERS", Instant.parse("2026-11-21T07:00:00Z"), "{\"deterministic\":{}}");

        var stocks = service.qualifiedStocks(USER, 40);
        assertThat(stocks).extracting(QualifiedStockService.QualifiedStock::symbol).containsExactly("INFY", "SBIN");
        var sbin = stocks.get(1);
        assertThat(sbin.runCount()).isEqualTo(2);
        assertThat(sbin.referenceEntry()).isEqualByComparingTo("812");
        assertThat(sbin.runKind()).isEqualTo("CONSOLIDATED_LEVEL_ONE");
        assertThat(sbin.qualified()).isTrue();
        assertThat(sbin.rank()).isEqualTo(1);
        assertThat(sbin.evidence()).containsEntry("TECHNICAL_ANALYSIS", "AVAILABLE").containsEntry("NEWS_AND_SENTIMENT", "MISSING");
        assertThat(stocks.get(0).qualified()).isFalse();
    }

    @Test
    void levelTwoValidatesSavesAndAttachesVerdict() throws Exception {
        runs.add("MARKET_SCREEN_FULL", "TOP_GAINERS", Instant.parse("2026-11-21T05:00:00Z"), marketScreen("SBIN", "LONG_WATCH", 800));
        AtomicReference<String> sent = new AtomicReference<>();
        String answer = """
                {"action":"BUY","entry_low":801,"entry_high":803,"stop_loss":792,"target_one":820,"target_two":790,
                 "confidence":71,"horizon":"INTRADAY",
                 "domains":{"technical":{"view":"SUPPORTIVE","note":"Breakout"},"fundamental":{"view":"NEUTRAL","note":"Fair value"},
                  "news_sentiment":{"view":"NO_EVIDENCE","note":""},"market_conditions":{"view":"SUPPORTIVE","note":"Banks strong"},
                  "institutional_flows":{"view":"NO_EVIDENCE","note":""},"corporate_actions":{"view":"NEUTRAL","note":""}},
                 "key_risks":["Nifty reversal"],"rationale":"Breakout confirmed."}
                """;
        var agent = new LevelTwoAgent(json, (key, request) -> { sent.set(request); return wrap(answer); }, "k", "qwen3:8b", runs,
                Clock.fixed(Instant.parse("2026-11-21T06:00:00Z"), ZoneOffset.UTC));
        var context = service.levelOneContext(USER, "NSE:SBIN-EQ", null, 40).orElseThrow();
        LevelTwoVerdict verdict = agent.analyze(USER, context);

        assertThat(sent.get()).contains("TECHNICAL_ANALYSIS").contains("RSI breakout evidence").contains("level_two_verdict");
        assertThat(verdict.action()).isEqualTo("BUY");
        assertThat(verdict.targetTwo()).isNull();
        assertThat(verdict.validationNotes()).anyMatch(n -> n.contains("Target 2"));
        assertThat(verdict.domains().get("technical").view()).isEqualTo("SUPPORTIVE");
        assertThat(runs.stored).anyMatch(r -> r.resultKind().equals(QualifiedStockService.LEVEL_TWO_KIND));

        var sbin = service.qualifiedStocks(USER, 40).getFirst();
        assertThat(sbin.levelTwo()).isNotNull();
        assertThat(sbin.levelTwo().confidence()).isEqualTo(71);
    }

    @Test
    void directionalCallWithStopOnWrongSideBecomesWait() throws Exception {
        runs.add("MARKET_SCREEN_FULL", "TOP_LOSERS", Instant.parse("2026-11-21T05:00:00Z"), marketScreen("INFY", "SHORT_WATCH", 1500));
        var agent = new LevelTwoAgent(json, (key, request) -> wrap("""
                {"action":"SELL","entry_low":1500,"entry_high":1500,"stop_loss":1490,"target_one":1470,"target_two":null,
                 "confidence":80,"horizon":"INTRADAY","domains":{},"key_risks":[],"rationale":"x"}"""), "k", "m", runs, Clock.systemUTC());
        var verdict = agent.analyze(USER, service.levelOneContext(USER, "INFY", null, 40).orElseThrow());
        assertThat(verdict.action()).isEqualTo("WAIT");
        assertThat(verdict.stopLoss()).isNull();
        assertThat(verdict.confidence()).isLessThanOrEqualTo(50);
        assertThat(verdict.domains().get("fundamental").view()).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void levelTwoWithoutProviderIsUnavailable() throws Exception {
        runs.add("MARKET_SCREEN_FULL", "TOP_GAINERS", Instant.now(), marketScreen("SBIN", "LONG_WATCH", 800));
        var agent = new LevelTwoAgent(json, null, "", "not configured", runs, Clock.systemUTC());
        assertThat(agent.available()).isFalse();
        assertThatThrownBy(() -> agent.analyze(USER, service.levelOneContext(USER, "SBIN", null, 40).orElseThrow()))
                .isInstanceOf(LevelTwoAgent.LevelTwoUnavailableException.class);
    }

    private String wrap(String text) {
        ObjectNode root = json.createObjectNode();
        root.putArray("output").addObject().put("type", "message").putArray("content").addObject()
                .put("type", "output_text").put("text", text);
        return root.toString();
    }

    private String marketScreen(String symbol, String stance, int entry) throws Exception {
        String evidence = json.writeValueAsString(json.readTree("""
                {"stocks":[{"symbol":"%s","evidence_by_domain":{
                  "TECHNICAL_ANALYSIS":[{"source":"TRENDLYNE","status":"SUCCESS","fetched_at":"2026-11-21T04:00:00Z","payload":{"summary":"RSI breakout evidence"}}],
                  "NEWS_AND_SENTIMENT":[{"source":"TRENDLYNE","status":"FAILED","detail":"timeout"}]}}]}""".formatted(symbol)));
        boolean shortSide = stance.equals("SHORT_WATCH");
        ObjectNode root = (ObjectNode) json.readTree("""
                {"deterministic":{"screenType":"TOP_GAINERS",
                  "ranking":{"candidates":[{"rank":1,"symbol":"%1$s","stance":"%2$s","score":82.5,"scoreBand":"HIGH","percentChange":3.1,"lastPrice":%3$d}]},
                  "analyses":[{"assessment":{"venue":"NSE","symbol":"%1$s","action":"%4$s","confirmationRule":"ORB","referenceEntry":%3$d,
                    "indicativeStop":%5$d,"indicativeTargetOne":%6$d,"rsi14":61.2}}]},
                 "ollama":{"model":"qwen3:8b","candidates":[{"symbol":"%1$s","stance":"%2$s","thesis":"t","confirmation":"c","risk":"r"}]},
                 "goldenCopy":{}}""".formatted(symbol, stance, entry, shortSide ? "SELL_CANDIDATE" : "BUY_CANDIDATE",
                shortSide ? entry + 10 : entry - 10, shortSide ? entry - 20 : entry + 20));
        ((ObjectNode) root.get("goldenCopy")).put("intelligenceEvidenceJson", evidence);
        return root.toString();
    }

    static final class MemoryRuns implements AnalysisRunHistoryStore {
        final List<StoredAnalysisRun> stored = new ArrayList<>();

        void add(String kind, String category, Instant at, String result) {
            append(new StoredAnalysisRun(UUID.randomUUID(), USER, category, kind, "f.csv", "0".repeat(64),
                    LocalDate.parse("2026-11-21"), 1, "qwen3:8b", at, result));
        }

        public void append(StoredAnalysisRun run) { stored.add(run); }

        public List<AnalysisRunSummary> findRecent(UUID userId, int limit) {
            return stored.stream().filter(r -> r.userId().equals(userId))
                    .sorted(Comparator.comparing(StoredAnalysisRun::createdAt).reversed()).limit(limit)
                    .map(r -> new AnalysisRunSummary(r.runId(), r.category(), r.resultKind(), r.fileName(), r.sourceSha256(),
                            r.tradingDate(), r.candidateCount(), r.stageOneModel(), r.createdAt())).toList();
        }

        public Optional<StoredAnalysisRun> findById(UUID userId, UUID runId) {
            return stored.stream().filter(r -> r.userId().equals(userId) && r.runId().equals(runId)).findFirst();
        }
    }
}
