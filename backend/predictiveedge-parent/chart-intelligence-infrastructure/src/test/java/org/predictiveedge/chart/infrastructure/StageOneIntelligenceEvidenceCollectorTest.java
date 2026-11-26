package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.EnumMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.SearchEvidenceCategory;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneActivityStore.StageOneAnalysisInput;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceFetchStatus;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.IntelligenceDataConnector;
import org.predictiveedge.intelligence.connector.ProviderEvidence;
import org.predictiveedge.intelligence.connector.ResourceEvidenceBundle;
import org.predictiveedge.intelligence.connector.SourceRole;

class StageOneIntelligenceEvidenceCollectorTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-14T06:00:00Z");

    @Test
    void capturesEveryDomainAndAllTwelveModulesOncePerDistinctSymbol() {
        var connector = new RecordingConnector(0);
        var result = new StageOneIntelligenceEvidenceCollector(connector, new ObjectMapper())
                .collect(List.of("INFY", "INFY"), CUTOFF);

        assertThat(result.at("/stocks")).hasSize(1);
        assertThat(result.at("/required_modules")).hasSize(DecisionResourceType.values().length);
        assertThat(result.at("/stocks/0/intelligence_modules"))
                .hasSize(DecisionResourceType.values().length);
        assertThat(result.at("/stocks/0/evidence_by_domain" ).size())
                .isEqualTo(EvidenceDomain.values().length);
        assertThat(result.at("/stocks/0/evidence_by_domain/TECHNICAL_ANALYSIS/0/source").asText())
                .isEqualTo("TRENDLYNE");
        assertThat(result.at("/stocks/0/evidence_by_domain/FUNDAMENTAL_ANALYSIS/0/payload/kind").asText())
                .isEqualTo("FUNDAMENTAL_ANALYSIS");
        assertThat(connector.calls).hasSize(EvidenceDomain.values().length);
    }

    @Test
    void fetchesEachDataTypeOncePerStockAndKeepsTheInputOrder() {
        var connector = new RecordingConnector(0);
        var result = new StageOneIntelligenceEvidenceCollector(connector, new ObjectMapper())
                .collect(List.of("infy", "TCS", "INFY"), CUTOFF);

        assertThat(result.at("/stocks").findValuesAsText("symbol")).containsExactly("INFY", "TCS");
        assertThat(connector.calls.values()).hasSize(2 * EvidenceDomain.values().length)
                .allMatch(count -> count.get() == 1);
        for (JsonNode stock : result.at("/stocks")) {
            assertThat(stock.at("/intelligence_modules")).hasSize(DecisionResourceType.values().length);
            for (EvidenceDomain domain : EvidenceDomain.values()) {
                assertThat(stock.at("/evidence_by_domain/" + domain.name())).hasSize(1);
            }
        }
    }

    @Test
    void runsFetchesOfDifferentDataTypesAtTheSameTime() {
        var connector = new RecordingConnector(20);
        new StageOneIntelligenceEvidenceCollector(connector, new ObjectMapper(), 2)
                .collect(List.of("INFY", "TCS"), CUTOFF);

        assertThat(connector.maxInFlight.get()).isGreaterThan(1);
    }

    @Test
    void handsEveryFetchedDataTypeToTheWriterBeforeCollating() {
        var writer = new RecordingWriter();
        var result = new StageOneIntelligenceEvidenceCollector(new RecordingConnector(0), new ObjectMapper())
                .collect(List.of("INFY", "TCS"), CUTOFF, null, writer);

        assertThat(writer.begun).containsExactly(List.of("INFY", "TCS"));
        assertThat(writer.writes).hasSize(2 * EvidenceDomain.values().length);
        assertThat(writer.writes.stream().map(write -> write.symbol() + ":" + write.domain()).distinct().count())
                .isEqualTo(2L * EvidenceDomain.values().length);
        assertThat(result.at("/stocks")).hasSize(2);
    }

    @Test
    void stopsWithTheOriginalFailureWhenAFetchFails() {
        var connector = new RecordingConnector(0);
        connector.failOn = EvidenceDomain.MARKET_DEPTH;

        assertThatThrownBy(() -> new StageOneIntelligenceEvidenceCollector(connector, new ObjectMapper())
                .collect(List.of("INFY", "TCS"), CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("depth feed down");
    }

    @Test
    void returnsAnEmptyEnvelopeWithoutCallingAnyWorkerWhenNoStockIsGiven() {
        var connector = new RecordingConnector(0);
        var writer = new RecordingWriter();
        var result = new StageOneIntelligenceEvidenceCollector(connector, new ObjectMapper())
                .collect(List.of(), CUTOFF, null, writer);

        assertThat(result.at("/stocks")).isEmpty();
        assertThat(writer.begun).isEmpty();
        assertThat(connector.calls).isEmpty();
    }

    @Test
    void rejectsAZeroWorkerGroup() {
        assertThatThrownBy(() -> new StageOneIntelligenceEvidenceCollector(new RecordingConnector(0),
                new ObjectMapper(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one worker");
    }

    @Test
    void storeWriterRecordsEachSelectionBeforeItsEvidence() {
        var store = new RecordingStore();
        var writer = new StageOneStoreEvidenceWriter(store, new ObjectMapper(), UUID.randomUUID(), UUID.randomUUID(),
                SearchEvidenceCategory.TOP_GAINERS, CUTOFF);
        new StageOneIntelligenceEvidenceCollector(new RecordingConnector(0), new ObjectMapper())
                .collect(List.of("INFY", "TCS"), CUTOFF, null, writer);

        assertThat(store.selectionStarts).hasSize(1);
        var start = store.selectionStarts.get(0);
        assertThat(start.stocks()).extracting(StageOneActivityStore.StockSelection::symbol)
                .containsExactly("INFY", "TCS");
        assertThat(store.evidence).hasSize(2 * EvidenceDomain.values().length);
        Map<String, UUID> recorded = writer.selectionIds();
        assertThat(store.evidence).allSatisfy(save -> {
            assertThat(store.selectionStarts.get(0).stocks())
                    .anyMatch(stock -> stock.selectionId().equals(save.selectionId()));
            assertThat(save.evidenceJson()).startsWith("[");
        });
        assertThat(recorded).containsOnlyKeys("INFY", "TCS");
        assertThat(store.evidence.stream().map(StageOneActivityStore.EvidenceSave::domain).distinct().count())
                .isEqualTo(EvidenceDomain.values().length);
    }

    @Test
    void collatesEachStockFromTheStageAfterItsWritesInsteadOfFromTheFetchResults() {
        var stage = new CollatingFromStoredRows();
        var result = new StageOneIntelligenceEvidenceCollector(new RecordingConnector(0), new ObjectMapper())
                .collect(List.of("INFY"), CUTOFF, null, stage);

        assertThat(stage.readSymbols).containsExactly("INFY");
        assertThat(result.at("/stocks/0/evidence_by_domain/MARKET_DATA/0/source").asText()).isEqualTo("STORED_ROW");
        assertThat(result.at("/stocks/0/evidence_by_domain/MARKET_DATA/0/payload/from").asText())
                .isEqualTo("database");
    }

    /** Writes nothing useful and returns a fixed row, so the test can tell whether collation reads the stage. */
    private static final class CollatingFromStoredRows implements EvidenceStage {
        private final List<String> readSymbols = new CopyOnWriteArrayList<>();

        @Override
        public void write(String symbol, EvidenceDomain domain, ArrayNode evidence) {
            // Fetch results are not kept; only the stored rows below are collated.
        }

        @Override
        public Map<EvidenceDomain, ArrayNode> read(String symbol) {
            readSymbols.add(symbol);
            var json = new ObjectMapper();
            var rows = json.createArrayNode();
            rows.addObject().put("domain", "MARKET_DATA").put("source", "STORED_ROW").put("role", "PRIMARY")
                    .put("status", "SUCCESS").put("fetched_at", CUTOFF.toString()).put("detail", "")
                    .putObject("payload").put("from", "database");
            Map<EvidenceDomain, ArrayNode> stored = new EnumMap<>(EvidenceDomain.class);
            for (EvidenceDomain domain : EvidenceDomain.values()) stored.put(domain, json.createArrayNode());
            stored.put(EvidenceDomain.MARKET_DATA, rows);
            return stored;
        }
    }

    private static final class RecordingConnector implements IntelligenceDataConnector {
        private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger maxInFlight = new AtomicInteger();
        private final long delayMillis;
        private EvidenceDomain failOn;

        RecordingConnector(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
            calls.computeIfAbsent(query.symbol() + ":" + domain, key -> new AtomicInteger()).incrementAndGet();
            int now = inFlight.incrementAndGet();
            maxInFlight.accumulateAndGet(now, Math::max);
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                if (domain == failOn) throw new IllegalStateException("depth feed down");
                return List.of(new ProviderEvidence(domain, EvidenceSource.TRENDLYNE, SourceRole.SUPPLEMENTARY,
                        EvidenceFetchStatus.SUCCESS, query.cutoff(), Map.of("kind", domain.name()), ""));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted", interrupted);
            } finally {
                inFlight.decrementAndGet();
            }
        }

        @Override
        public ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query) {
            throw new UnsupportedOperationException();
        }
    }

    private record Write(String symbol, EvidenceDomain domain, ArrayNode evidence) { }

    private static final class RecordingWriter implements EvidenceStage {
        private final List<List<String>> begun = new ArrayList<>();
        private final ConcurrentLinkedQueue<Write> writes = new ConcurrentLinkedQueue<>();

        @Override
        public Map<EvidenceDomain, ArrayNode> read(String symbol) {
            Map<EvidenceDomain, ArrayNode> stored = new EnumMap<>(EvidenceDomain.class);
            writes.stream().filter(write -> write.symbol().equals(symbol))
                    .forEach(write -> stored.put(write.domain(), write.evidence()));
            return stored;
        }

        @Override
        public void begin(List<String> symbols) {
            begun.add(List.copyOf(symbols));
        }

        @Override
        public void write(String symbol, EvidenceDomain domain, ArrayNode evidence) {
            writes.add(new Write(symbol, domain, evidence));
        }
    }

    private static final class RecordingStore implements StageOneActivityStore {
        private final List<SelectionStart> selectionStarts = new ArrayList<>();
        private final ConcurrentLinkedQueue<EvidenceSave> evidence = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<UUID> analyses = new ConcurrentLinkedQueue<>();

        @Override
        public void recordSearch(ScreenerSearch search) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordSelections(SelectionBatch batch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordIntradayList(IntradayListSave list) {
            throw new UnsupportedOperationException();
        }

        @Override
        public synchronized void recordSelectedStocks(SelectionStart start) {
            selectionStarts.add(start);
        }

        @Override
        public void recordEvidence(EvidenceSave save) {
            evidence.add(save);
        }

        @Override
        public void recordAnalysis(UUID selectionId, StageOneAnalysisInput analysis) {
            analyses.add(selectionId);
        }

        @Override
        public Map<String, UUID> selectionIdsBySymbol(UUID userId, UUID searchId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String evidenceByDomainJson(UUID selectionId) {
            ObjectNode byDomain = new ObjectMapper().createObjectNode();
            for (EvidenceDomain domain : EvidenceDomain.values()) byDomain.putArray(domain.name());
            for (EvidenceSave save : evidence) {
                if (!save.selectionId().equals(selectionId)) continue;
                try {
                    ArrayNode items = (ArrayNode) byDomain.get(save.domain());
                    new ObjectMapper().readTree(save.evidenceJson()).forEach(items::add);
                } catch (JsonProcessingException failure) {
                    throw new IllegalStateException(failure);
                }
            }
            return byDomain.toString();
        }
    }
}
