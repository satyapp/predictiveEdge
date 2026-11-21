package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceFetchStatus;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.IntelligenceDataConnector;
import org.predictiveedge.intelligence.connector.IntelligenceSourcePlan;
import org.predictiveedge.intelligence.connector.ProviderEvidence;

/** Builds the complete twelve-module evidence envelope used by Stage-1 and preserved for Stage-2. */
final class StageOneIntelligenceEvidenceCollector {
    static final String SCHEMA_VERSION = "stage-one-golden-copy-v1";

    private final IntelligenceDataConnector connector;
    private final ObjectMapper json;

    StageOneIntelligenceEvidenceCollector(IntelligenceDataConnector connector, ObjectMapper json) {
        this.connector = Objects.requireNonNull(connector, "Intelligence data connector is required");
        this.json = Objects.requireNonNull(json, "Object mapper is required");
    }

    ObjectNode collect(Collection<String> symbols, Instant cutoff) {
        ObjectNode root = json.createObjectNode();
        root.put("schema_version", SCHEMA_VERSION);
        root.put("evidence_cutoff", cutoff.toString());
        root.put("required_module_count", DecisionResourceType.values().length);
        ArrayNode requiredModules = root.putArray("required_modules");
        for (DecisionResourceType resource : DecisionResourceType.values()) requiredModules.add(resource.name());
        ArrayNode stocks = root.putArray("stocks");
        for (String symbol : new LinkedHashSet<>(symbols)) stocks.add(stock(symbol, cutoff));
        return root;
    }

    private ObjectNode stock(String symbol, Instant cutoff) {
        ObjectNode stock = json.createObjectNode();
        stock.put("symbol", symbol);
        ObjectNode evidenceByDomain = stock.putObject("evidence_by_domain");
        Map<EvidenceDomain, ArrayNode> evidence = new EnumMap<>(EvidenceDomain.class);
        for (EvidenceDomain domain : EvidenceDomain.values()) {
            ArrayNode values = evidenceByDomain.putArray(domain.name());
            connector.fetch(domain, EvidenceQuery.forSymbol(symbol, query(domain, symbol), cutoff))
                    .forEach(value -> values.add(provider(value)));
            evidence.put(domain, values);
        }

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
            default -> domain.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT) + " for " + symbol;
        };
    }
}
