package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.predictiveedge.intelligence.connector.EvidenceDomain;

/** Keeps evidence in memory for a run that is not saved to the database. Thread-safe. */
final class InMemoryEvidenceStage implements EvidenceStage {
    private final Map<String, Map<EvidenceDomain, ArrayNode>> evidence = new ConcurrentHashMap<>();

    @Override
    public void write(String symbol, EvidenceDomain domain, ArrayNode values) {
        evidence.computeIfAbsent(symbol, key -> new ConcurrentHashMap<>()).put(domain, values);
    }

    @Override
    public Map<EvidenceDomain, ArrayNode> read(String symbol) {
        Map<EvidenceDomain, ArrayNode> copy = new EnumMap<>(EvidenceDomain.class);
        copy.putAll(evidence.getOrDefault(symbol, Map.of()));
        return copy;
    }
}
