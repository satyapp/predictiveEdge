package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceFetchStatus;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceRoute;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.IntelligenceDataConnector;
import org.predictiveedge.intelligence.connector.IntelligenceSourcePlan;
import org.predictiveedge.intelligence.connector.ProviderEvidence;

/**
 * Builds the complete twelve-module evidence envelope used by Stage-1 and preserved for Stage-2.
 *
 * <p>The work runs in three kinds of threads. Each evidence domain (data type) has its own group of fetch workers,
 * and every (stock, domain) pair is fetched by exactly one worker. A worker writes its result to the
 * {@link EvidenceStage} before it finishes. When every domain for a stock has been written, a collator thread reads
 * that stock back from the stage and builds its package. In a persisted run the stage is the database, so the
 * package is built from stored rows, not from the fetch results held in memory. The envelope lists stocks in the
 * order they were given.</p>
 *
 * <p>A failed fetch or write stops the run: the first failure is rethrown after the running work has settled.</p>
 */
final class StageOneIntelligenceEvidenceCollector {
    static final String SCHEMA_VERSION = "stage-one-golden-copy-v1";
    static final int DEFAULT_WORKERS_PER_DATA_TYPE = 2;
    static final int COLLATOR_THREADS = 2;
    /** Domains whose FYERS route is the candle history that the analysis already loads. */
    private static final Set<EvidenceDomain> REUSABLE_CANDLE_DOMAINS =
            Set.of(EvidenceDomain.MARKET_DATA, EvidenceDomain.HISTORICAL_AND_PREDICTIVE);

    private final IntelligenceDataConnector connector;
    private final ObjectMapper json;
    private final int workersPerDataType;

    StageOneIntelligenceEvidenceCollector(IntelligenceDataConnector connector, ObjectMapper json) {
        this(connector, json, DEFAULT_WORKERS_PER_DATA_TYPE);
    }

    StageOneIntelligenceEvidenceCollector(IntelligenceDataConnector connector, ObjectMapper json,
            int workersPerDataType) {
        this.connector = Objects.requireNonNull(connector, "Intelligence data connector is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
        if (workersPerDataType < 1) throw new IllegalArgumentException("At least one worker per data type is required");
        this.workersPerDataType = workersPerDataType;
    }

    /**
     * The same collector, except that the pre-open evidence of each stock of the search comes from that stock's row
     * in the user's pre-open run for the search's trading day.
     */
    StageOneIntelligenceEvidenceCollector withPreOpenRows(StageOneActivityStore store, UUID userId, UUID searchId) {
        return new StageOneIntelligenceEvidenceCollector(
                new PreOpenRowEvidenceConnector(connector, store, userId, searchId), json, workersPerDataType);
    }

    ObjectNode collect(Collection<String> symbols, Instant cutoff) {
        return collect(symbols, cutoff, null);
    }

    /** Collects in memory, without saving. */
    ObjectNode collect(Collection<String> symbols, Instant cutoff, BrokerContext brokerContext) {
        return collect(symbols, cutoff, brokerContext, new InMemoryEvidenceStage());
    }

    /** Fetches every stock in parallel, stores each result in {@code stage}, and returns the collated envelope. */
    ObjectNode collect(Collection<String> symbols, Instant cutoff, BrokerContext brokerContext, EvidenceStage stage) {
        return collect(symbols, cutoff, brokerContext, stage, Map.of());
    }

    /**
     * As {@link #collect(Collection, Instant, BrokerContext, EvidenceStage)}, but a stock with candles in
     * {@code analysedCandles} (keyed by upper-case symbol) gets its FYERS market-data and historical evidence from those
     * candles, so no source is called for those domains. Other stocks are fetched through the connector as usual.
     */
    ObjectNode collect(Collection<String> symbols, Instant cutoff, BrokerContext brokerContext, EvidenceStage stage,
            Map<String, List<Candle>> analysedCandles) {
        Objects.requireNonNull(cutoff, "Evidence cutoff is required");
        Objects.requireNonNull(analysedCandles, "Analysed candles are required");
        Objects.requireNonNull(stage, "Evidence stage is required");
        ObjectNode root = json.createObjectNode();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("evidence_cutoff", cutoff.toString());
        root.put("required_module_count", DecisionResourceType.values().length);
        ArrayNode requiredModules = root.putArray("required_modules");
        for (DecisionResourceType resource : DecisionResourceType.values()) requiredModules.add(resource.name());
        ArrayNode stocks = root.putArray("stocks");
        List<String> distinct = symbols.stream()
                .map(symbol -> Objects.requireNonNull(symbol, "Symbol is required").trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (distinct.isEmpty()) return root;
        stage.begin(distinct);
        runParallel(distinct, cutoff, brokerContext, stage, analysedCandles).forEach(stocks::add);
        return root;
    }

    private List<ObjectNode> runParallel(List<String> symbols, Instant cutoff, BrokerContext brokerContext,
            EvidenceStage stage, Map<String, List<Candle>> analysedCandles) {
        Map<EvidenceDomain, ExecutorService> groups = new EnumMap<>(EvidenceDomain.class);
        ExecutorService collators = Executors.newFixedThreadPool(COLLATOR_THREADS, threads("stage1-collator"));
        try {
            for (EvidenceDomain domain : EvidenceDomain.values()) {
                groups.put(domain, Executors.newFixedThreadPool(workersPerDataType,
                        threads("stage1-fetch-" + domain.name())));
            }
            List<CompletableFuture<ObjectNode>> packages = new ArrayList<>();
            for (String symbol : symbols) {
                Map<EvidenceDomain, CompletableFuture<Void>> writes = new EnumMap<>(EvidenceDomain.class);
                for (EvidenceDomain domain : EvidenceDomain.values()) {
                    writes.put(domain, CompletableFuture.runAsync(
                            () -> fetchAndWrite(symbol, domain, cutoff, brokerContext, analysedCandles, stage),
                            groups.get(domain)));
                }
                packages.add(CompletableFuture.allOf(writes.values().toArray(CompletableFuture[]::new))
                        .thenApplyAsync(done -> collate(symbol, stage), collators));
            }
            awaitAll(packages);
            return packages.stream().map(CompletableFuture::join).toList();
        } finally {
            groups.values().forEach(ExecutorService::shutdownNow);
            collators.shutdownNow();
        }
    }

    private void fetchAndWrite(String symbol, EvidenceDomain domain, Instant cutoff,
            BrokerContext brokerContext, Map<String, List<Candle>> analysedCandles, EvidenceStage stage) {
        ArrayNode values = json.createArrayNode();
        List<ProviderEvidence> results = reusedCandleEvidence(domain, analysedCandles.get(symbol), cutoff);
        if (results == null) {
            results = connector.fetch(domain, new EvidenceQuery(symbol, query(domain, symbol), cutoff, brokerContext,
                    null, Map.of()));
        }
        results.forEach(value -> values.add(provider(value)));
        stage.write(symbol, domain, values);
    }

    /**
     * Evidence built from candles the analysis already loaded from FYERS, or {@code null} when this domain or these
     * candles do not allow it. FYERS is the first source for these domains, so the rest of the plan is recorded as
     * SKIPPED, as the composite does once an earlier source succeeds. {@code fetchedAt} is the evidence cutoff, which
     * is taken after the analysis loaded the candles. Zerodha gets no request here: it has no lookup of its own yet,
     * so for stocks without analysed candles it stays UNAVAILABLE.
     */
    private static List<ProviderEvidence> reusedCandleEvidence(EvidenceDomain domain, List<Candle> candles,
            Instant fetchedAt) {
        if (candles == null || candles.isEmpty() || !REUSABLE_CANDLE_DOMAINS.contains(domain)) return null;
        List<EvidenceRoute> routes = IntelligenceSourcePlan.routesFor(domain).stream()
                .sorted(Comparator.comparingInt(EvidenceRoute::priority)).toList();
        if (routes.isEmpty() || routes.get(0).source() != EvidenceSource.FYERS) return null;
        var results = new ArrayList<ProviderEvidence>();
        for (int index = 0; index < routes.size(); index++) {
            EvidenceRoute route = routes.get(index);
            results.add(index == 0
                    ? new ProviderEvidence(domain, route.source(), route.role(), EvidenceFetchStatus.SUCCESS,
                            fetchedAt, List.copyOf(candles),
                            "Candles reused from this run's analysis; no extra FYERS call")
                    : new ProviderEvidence(domain, route.source(), route.role(), EvidenceFetchStatus.SKIPPED,
                            fetchedAt, null, "Not called: an earlier source returned usable evidence"));
        }
        return List.copyOf(results);
    }

    /** Waits for every future, then rethrows the first failure with its original type. */
    private static void awaitAll(List<CompletableFuture<ObjectNode>> packages) {
        try {
            CompletableFuture.allOf(packages.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Stage-1 evidence collection failed", cause);
        }
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private ObjectNode collate(String symbol, EvidenceStage stage) {
        Map<EvidenceDomain, ArrayNode> stored = stage.read(symbol);
        Map<EvidenceDomain, ArrayNode> evidence = new EnumMap<>(EvidenceDomain.class);
        for (EvidenceDomain domain : EvidenceDomain.values()) {
            evidence.put(domain, stored.getOrDefault(domain, json.createArrayNode()));
        }

        ObjectNode stock = json.createObjectNode();
        stock.put("symbol", symbol);
        ObjectNode evidenceByDomain = stock.putObject("evidence_by_domain");
        for (EvidenceDomain domain : EvidenceDomain.values()) evidenceByDomain.set(domain.name(), evidence.get(domain));

        ArrayNode modules = stock.putArray("intelligence_modules");
        for (DecisionResourceType resource : DecisionResourceType.values()) {
            ObjectNode module = modules.addObject();
            module.put("module", resource.name());
            ArrayNode domains = module.putArray("evidence_domains");
            int available = 0;
            for (EvidenceDomain domain : IntelligenceSourcePlan.domainsFor(resource)) {
                domains.add(domain.name());
                if (hasSuccess(evidence.get(domain))) available++;
            }
            module.put("evidence_coverage", available == 0 ? "NO_EVIDENCE"
                    : available == domains.size() ? "ALL_DOMAINS_HAVE_EVIDENCE" : "PARTIAL_EVIDENCE");
            module.put("decision_resource_ready", false);
            module.put("readiness_detail", "Evidence coverage is not a governed decision-resource readiness result");
            module.put("available_domain_count", available);
            module.put("required_domain_count", domains.size());
        }
        return stock;
    }

    private ObjectNode provider(ProviderEvidence value) {
        ObjectNode evidence = json.createObjectNode();
        evidence.put("domain", value.domain().name());
        evidence.put("source", value.source().name());
        evidence.put("role", value.role().name());
        evidence.put("status", value.status().name());
        evidence.put("fetched_at", value.fetchedAt().toString());
        if (value.payload() == null) evidence.putNull("payload");
        else evidence.set("payload", json.valueToTree(value.payload()));
        evidence.put("detail", value.detail());
        return evidence;
    }

    private static boolean hasSuccess(ArrayNode evidence) {
        for (var value : evidence) {
            if (EvidenceFetchStatus.SUCCESS.name().equals(value.path("status").asText())) return true;
        }
        return false;
    }

    private static String query(EvidenceDomain domain, String symbol) {
        return switch (domain) {
            case TECHNICAL_ANALYSIS -> "technical analysis for " + symbol;
            case FUNDAMENTAL_ANALYSIS -> "SWOT strengths weaknesses opportunities threats for " + symbol;
            case MARKET_DEPTH -> "live bid ask market depth for " + symbol;
            case PRE_OPEN_ANALYSIS -> "current pre-open market state for " + symbol;
            default -> domain.name().replace('_', ' ').toLowerCase(Locale.ROOT) + " for " + symbol;
        };
    }
}
