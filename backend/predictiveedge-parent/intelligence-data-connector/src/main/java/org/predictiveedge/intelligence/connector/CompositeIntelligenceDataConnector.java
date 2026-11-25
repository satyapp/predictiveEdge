package org.predictiveedge.intelligence.connector;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.predictiveedge.decision.domain.DecisionResourceType;

public final class CompositeIntelligenceDataConnector implements IntelligenceDataConnector {
    private final Map<EvidenceSource, EvidenceSourceConnector> connectors;
    private final Clock clock;

    public CompositeIntelligenceDataConnector(List<EvidenceSourceConnector> connectors, Clock clock) {
        Objects.requireNonNull(connectors, "Source connectors are required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        var indexed = new EnumMap<EvidenceSource, EvidenceSourceConnector>(EvidenceSource.class);
        for (EvidenceSourceConnector connector : connectors) {
            EvidenceSourceConnector duplicate = indexed.put(connector.source(), connector);
            if (duplicate != null) throw new IllegalArgumentException("Duplicate connector for " + connector.source());
        }
        this.connectors = Map.copyOf(indexed);
    }

    /**
     * Walks the domain's source plan in priority order. A source is called only while no earlier source has
     * returned usable evidence for this domain and symbol; later sources are recorded as {@code SKIPPED} so the
     * result still lists the full plan and shows which source supplied the data.
     */
    @Override
    public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
        Objects.requireNonNull(domain, "Evidence domain is required");
        Objects.requireNonNull(query, "Evidence query is required");
        var results = new ArrayList<ProviderEvidence>();
        boolean satisfied = false;
        for (EvidenceRoute route : IntelligenceSourcePlan.routesFor(domain).stream()
                .sorted(Comparator.comparingInt(EvidenceRoute::priority))
                .toList()) {
            if (satisfied) {
                results.add(result(route, EvidenceFetchStatus.SKIPPED, null,
                        "Not called: an earlier source returned usable evidence"));
                continue;
            }
            ProviderEvidence attempt = fetch(route, query);
            results.add(attempt);
            satisfied = attempt.status() == EvidenceFetchStatus.SUCCESS;
        }
        return List.copyOf(results);
    }

    @Override
    public ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query) {
        var evidence = new ArrayList<ProviderEvidence>();
        IntelligenceSourcePlan.domainsFor(resourceType).stream()
                .sorted(Comparator.comparing(Enum::ordinal))
                .forEach(domain -> evidence.addAll(fetch(domain, query)));
        return new ResourceEvidenceBundle(resourceType, query, evidence);
    }

    private ProviderEvidence fetch(EvidenceRoute route, EvidenceQuery query) {
        EvidenceSourceConnector connector = connectors.get(route.source());
        if (connector == null) {
            return result(route, EvidenceFetchStatus.UNAVAILABLE, null, "Connector is not configured");
        }
        if (!connector.supports(route.domain())) {
            return result(route, EvidenceFetchStatus.UNSUPPORTED, null,
                    "The source does not expose this evidence domain");
        }
        try {
            Object payload = connector.fetch(route.domain(), query);
            if (payload == null || isEmptyCollection(payload)) {
                return result(route, EvidenceFetchStatus.UNAVAILABLE, null, "No evidence returned");
            }
            return result(route, EvidenceFetchStatus.SUCCESS, payload, "");
        } catch (EvidenceUnavailableException unavailable) {
            return result(route, EvidenceFetchStatus.UNAVAILABLE, null, unavailable.getMessage());
        } catch (RuntimeException failure) {
            return result(route, EvidenceFetchStatus.FAILED, null,
                    "Connector failure: " + failure.getClass().getSimpleName());
        }
    }

    private static boolean isEmptyCollection(Object payload) {
        return payload instanceof Collection<?> collection && collection.isEmpty();
    }

    private ProviderEvidence result(
            EvidenceRoute route, EvidenceFetchStatus status, Object payload, String detail) {
        return new ProviderEvidence(route.domain(), route.source(), route.role(), status,
                clock.instant(), payload, detail);
    }
}
