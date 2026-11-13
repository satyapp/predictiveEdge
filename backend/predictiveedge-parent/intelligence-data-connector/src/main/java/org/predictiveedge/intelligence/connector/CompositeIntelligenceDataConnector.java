package org.predictiveedge.intelligence.connector;

import java.time.Clock;
import java.util.ArrayList;
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

    @Override
    public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
        Objects.requireNonNull(domain, "Evidence domain is required");
        Objects.requireNonNull(query, "Evidence query is required");
        return IntelligenceSourcePlan.routesFor(domain).stream()
                .sorted(Comparator.comparingInt(EvidenceRoute::priority))
                .map(route -> fetch(route, query))
                .toList();
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
            if (payload == null) return result(route, EvidenceFetchStatus.UNAVAILABLE, null, "No evidence returned");
            return result(route, EvidenceFetchStatus.SUCCESS, payload, "");
        } catch (EvidenceUnavailableException unavailable) {
            return result(route, EvidenceFetchStatus.UNAVAILABLE, null, unavailable.getMessage());
        } catch (RuntimeException failure) {
            return result(route, EvidenceFetchStatus.FAILED, null,
                    "Connector failure: " + failure.getClass().getSimpleName());
        }
    }

    private ProviderEvidence result(
            EvidenceRoute route, EvidenceFetchStatus status, Object payload, String detail) {
        return new ProviderEvidence(route.domain(), route.source(), route.role(), status,
                clock.instant(), payload, detail);
    }
}
