package org.predictiveedge.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;
import org.predictiveedge.decision.infrastructure.OpenAiResponsesTransport;
import org.predictiveedge.pipeline.QualifiedStockService.LevelOneContext;

/**
 * Level-2 AI agent. It reviews one level-1 qualified stock against the full evidence preserved in its Stage-1
 * golden copy (technical, fundamental, news and sentiment, macro and market conditions, flows, corporate actions)
 * and returns a structured, validated verdict that is saved to analysis history.
 */
public final class LevelTwoAgent {
    static final List<String> VIEW_DOMAINS = List.of("technical", "fundamental", "news_sentiment", "market_conditions",
            "institutional_flows", "corporate_actions");
    static final String SYSTEM_PROMPT = """
            You are the level-2 equity analyst agent of PredictiveEdge, an advisory system for one Indian retail trader.
            A level-1 agent has already screened and qualified this NSE stock. Re-underwrite it independently using the
            supplied point-in-time evidence: technical analysis, chart patterns, fundamentals, news and sentiment,
            corporate actions, macro and market conditions, institutional flows and historical behaviour.
            Judge each domain as SUPPORTIVE, NEUTRAL, ADVERSE or NO_EVIDENCE for the trade, with a one-sentence note.
            Return BUY or SELL only when the evidence is consistent and the reward clearly exceeds the risk; otherwise
            WAIT, or NO_TRADE when the setup is invalid. You may disagree with level 1. Never invent prices, news or
            numbers that are not in the evidence. For BUY the stop must be below the entry and targets above it; for
            SELL the reverse. Use null for levels that do not apply. Never claim to place an order. Keep the rationale
            under 150 words.
            """;

    private final ObjectMapper json;
    private final OpenAiResponsesTransport transport;
    private final String apiKey;
    private final String model;
    private final AnalysisRunHistoryStore runs;
    private final Clock clock;

    public LevelTwoAgent(ObjectMapper json, OpenAiResponsesTransport transport, String apiKey, String model,
            AnalysisRunHistoryStore runs, Clock clock) {
        this.json = Objects.requireNonNull(json);
        this.transport = transport;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.runs = Objects.requireNonNull(runs);
        this.clock = Objects.requireNonNull(clock);
    }

    public boolean available() { return transport != null; }

    public String model() { return model; }

    public LevelTwoVerdict analyze(UUID userId, LevelOneContext context) {
        if (transport == null) {
            throw new LevelTwoUnavailableException("Level 2 needs an AI provider. Set PE_AI_PROVIDER to ollama or openai.");
        }
        ObjectNode input = input(context);
        String raw;
        try {
            raw = transport.createResponse(apiKey, json.writeValueAsString(request(input)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Level-2 request could not be serialized", failure);
        } catch (RuntimeException failure) {
            throw new LevelTwoUnavailableException("The level-2 AI agent did not answer: " + failure.getMessage());
        }
        LevelTwoVerdict verdict = validate(context, parse(raw));
        save(userId, context, input, verdict);
        return verdict;
    }

    ObjectNode input(LevelOneContext context) {
        var stock = context.stock();
        ObjectNode root = json.createObjectNode();
        root.put("symbol", stock.symbol());
        root.put("exchange", stock.venue());
        root.put("trading_date", String.valueOf(stock.tradingDate()));
        root.put("screen", stock.category());
        ObjectNode levelOne = root.putObject("level_one");
        levelOne.put("stance", stock.stance());
        levelOne.put("thesis", stock.thesis());
        levelOne.put("confirmation", stock.confirmation());
        levelOne.put("risk", stock.risk());
        levelOne.put("setup_action", stock.setupAction());
        levelOne.put("confirmation_rule", stock.confirmationRule());
        levelOne.put("reference_entry", stock.referenceEntry());
        levelOne.put("indicative_stop", stock.indicativeStop());
        levelOne.put("target_one", stock.targetOne());
        levelOne.put("target_two", stock.targetTwo());
        levelOne.put("last_price", stock.lastPrice());
        levelOne.put("percent_change", stock.percentChange());
        levelOne.put("rsi14", stock.rsi14());
        levelOne.put("atr14", stock.atr14());
        levelOne.put("screen_score", stock.score());
        ObjectNode evidence = root.putObject("evidence_by_domain");
        for (String domain : QualifiedStockService.DOMAINS) {
            ArrayNode items = evidence.putArray(domain);
            int kept = 0;
            for (JsonNode provider : context.evidence().path("evidence_by_domain").path(domain)) {
                if (!"SUCCESS".equals(provider.path("status").asText()) || kept++ >= 3) continue;
                ObjectNode item = items.addObject();
                item.put("source", provider.path("source").asText());
                item.put("fetched_at", provider.path("fetched_at").asText());
                String payload = provider.path("payload").isMissingNode() ? provider.path("detail").asText("")
                        : provider.path("payload").toString();
                item.put("content", payload.length() > 1500 ? payload.substring(0, 1497) + "..." : payload);
            }
        }
        return root;
    }

    ObjectNode request(ObjectNode input) throws JsonProcessingException {
        ObjectNode request = json.createObjectNode();
        request.put("model", model);
        ArrayNode messages = request.putArray("input");
        message(messages, "system", SYSTEM_PROMPT);
        message(messages, "user", "Level-2 review of this level-1 qualified stock:\n" + json.writeValueAsString(input));
        ObjectNode format = request.putObject("text").putObject("format");
        format.put("type", "json_schema");
        format.put("name", "level_two_verdict");
        format.put("strict", true);
        format.set("schema", schema());
        return request;
    }

    private ObjectNode schema() {
        ObjectNode schema = object();
        ObjectNode p = (ObjectNode) schema.get("properties");
        p.putObject("action").put("type", "string").putArray("enum").add("BUY").add("SELL").add("WAIT").add("NO_TRADE");
        for (String n : List.of("entry_low", "entry_high", "stop_loss", "target_one", "target_two")) {
            p.putObject(n).putArray("type").add("number").add("null");
        }
        p.putObject("confidence").put("type", "integer").put("minimum", 0).put("maximum", 100);
        p.putObject("horizon").put("type", "string").putArray("enum").add("INTRADAY").add("SWING").add("POSITIONAL");
        ObjectNode domains = object();
        for (String domain : VIEW_DOMAINS) {
            ObjectNode view = object();
            ((ObjectNode) view.get("properties")).putObject("view").put("type", "string").putArray("enum")
                    .add("SUPPORTIVE").add("NEUTRAL").add("ADVERSE").add("NO_EVIDENCE");
            ((ObjectNode) view.get("properties")).putObject("note").put("type", "string");
            required(view);
            ((ObjectNode) domains.get("properties")).set(domain, view);
        }
        required(domains);
        p.set("domains", domains);
        p.putObject("key_risks").put("type", "array").set("items", json.createObjectNode().put("type", "string"));
        p.putObject("rationale").put("type", "string");
        required(schema);
        return schema;
    }

    private ObjectNode object() {
        ObjectNode node = json.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        node.putObject("properties");
        return node;
    }

    private void required(ObjectNode node) {
        ArrayNode required = node.putArray("required");
        node.get("properties").fieldNames().forEachRemaining(required::add);
    }

    private void message(ArrayNode input, String role, String text) {
        ObjectNode message = input.addObject();
        message.put("role", role);
        message.putArray("content").addObject().put("type", "input_text").put("text", text);
    }

    private JsonNode parse(String raw) {
        try {
            JsonNode response = json.readTree(raw);
            for (JsonNode item : response.path("output")) {
                if (!"message".equals(item.path("type").asText())) continue;
                for (JsonNode content : item.path("content")) {
                    if ("refusal".equals(content.path("type").asText())) throw new LevelTwoUnavailableException("The AI model refused the request");
                    if ("output_text".equals(content.path("type").asText())) return json.readTree(content.path("text").asText());
                }
            }
        } catch (JsonProcessingException failure) {
            throw new LevelTwoUnavailableException("The AI model returned invalid JSON");
        }
        throw new LevelTwoUnavailableException("The AI model returned no structured answer");
    }

    /** Enforces price-level sanity; a directional call with inconsistent levels is downgraded to WAIT. */
    LevelTwoVerdict validate(LevelOneContext context, JsonNode o) {
        List<String> notes = new ArrayList<>();
        String action = o.path("action").asText("WAIT");
        if (!List.of("BUY", "SELL", "WAIT", "NO_TRADE").contains(action)) { notes.add("Unknown action " + action + " treated as WAIT"); action = "WAIT"; }
        BigDecimal low = decimal(o, "entry_low"), high = decimal(o, "entry_high"), stop = decimal(o, "stop_loss");
        BigDecimal t1 = decimal(o, "target_one"), t2 = decimal(o, "target_two");
        if (low == null) low = high;
        if (high == null) high = low;
        if (low != null && low.compareTo(high) > 0) { BigDecimal swap = low; low = high; high = swap; }
        if (action.equals("BUY") || action.equals("SELL")) {
            boolean buy = action.equals("BUY");
            String problem = null;
            if (low == null || stop == null) problem = "no entry or stop";
            else if (buy ? stop.compareTo(low) >= 0 : stop.compareTo(high) <= 0) problem = "stop on the wrong side of the entry";
            else if (t1 != null && (buy ? t1.compareTo(high) <= 0 : t1.compareTo(low) >= 0)) problem = "target 1 on the wrong side of the entry";
            if (problem != null) {
                notes.add("Model proposed " + action + " with " + problem + "; downgraded to WAIT.");
                action = "WAIT";
            }
            if (t2 != null && (buy ? t2.compareTo(high) <= 0 : t2.compareTo(low) >= 0)) { notes.add("Target 2 dropped: wrong side of entry."); t2 = null; }
        }
        boolean directional = action.equals("BUY") || action.equals("SELL");
        Map<String, LevelTwoVerdict.DomainView> domains = new LinkedHashMap<>();
        for (String domain : VIEW_DOMAINS) {
            JsonNode view = o.path("domains").path(domain);
            domains.put(domain, new LevelTwoVerdict.DomainView(view.path("view").asText("NO_EVIDENCE"), view.path("note").asText("")));
        }
        List<String> risks = new ArrayList<>();
        o.path("key_risks").forEach(r -> risks.add(r.asText()));
        int confidence = Math.max(0, Math.min(100, o.path("confidence").asInt(0)));
        return new LevelTwoVerdict(LevelTwoVerdict.SCHEMA_VERSION, context.stock().symbol(), context.run().runId(),
                context.stock().stance(), clock.instant(), model, action,
                directional ? low : null, directional ? high : null, directional ? stop : null,
                directional ? t1 : null, directional ? t2 : null, directional ? confidence : Math.min(confidence, 50),
                o.path("horizon").asText("INTRADAY"), domains, risks, o.path("rationale").asText(""), notes);
    }

    private void save(UUID userId, LevelOneContext context, ObjectNode input, LevelTwoVerdict verdict) {
        try {
            runs.append(new AnalysisRunHistoryStore.StoredAnalysisRun(UUID.randomUUID(), userId, "LEVEL_TWO",
                    QualifiedStockService.LEVEL_TWO_KIND, "level-two-" + verdict.symbol() + ".json",
                    sha256(json.writeValueAsString(input)), context.run().tradingDate(), 1, model,
                    verdict.createdAt(), json.writeValueAsString(verdict)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Level-2 verdict could not be saved", failure);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() && value.decimalValue().signum() > 0 ? value.decimalValue() : null;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static final class LevelTwoUnavailableException extends RuntimeException {
        public LevelTwoUnavailableException(String message) { super(message); }
    }
}
