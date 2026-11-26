package org.predictiveedge.intelligence.connector;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.predictiveedge.decision.domain.DecisionResourceType;

public final class CompositeIntelligenceDataConnector implements IntelligenceDataConnector {
    private final List<EvidenceSourceConnector> connectors;
    private final Clock clock;

    /**
     * One source may have several connectors when each serves different domains (for example FYERS candles and
     * FYERS market depth). Two connectors for the same source and domain are rejected at construction.
     */
    public CompositeIntelligenceDataConnector(List<EvidenceSourceConnector> connectors, Clock clock) {
        Objects.requireNonNull(connectors, "Source connectors are required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
        var seen = new HashSet<SourceDomain>();
        for (EvidenceSourceConnector connector : connectors) {
            for (EvidenceDomain domain : EvidenceDomain.values()) {
                if (connector.supports(domain) && !seen.add(new SourceDomain(connector.source(), domain))) {
                    throw new IllegalArgumentException(
                            "Duplicate connector for " + connector.source() + " " + domain);
                }
            }
        }
        this.connectors = List.copyOf(connectors);
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
        EvidenceSourceConnector connector = connectorFor(route);
        if (connector == null) {
            boolean configured = connectors.stream().anyMatch(candidate -> candidate.source() == route.source());
            return configured
                    ? result(route, EvidenceFetchStatus.UNSUPPORTED, null,
                            "The source does not expose this evidence domain")
                    : result(route, EvidenceFetchStatus.UNAVAILABLE, null, "Connector is not configured");
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

    private EvidenceSourceConnector connectorFor(EvidenceRoute route) {
        return connectors.stream()
                .filter(candidate -> candidate.source() == route.source() && candidate.supports(route.domain()))
                .findFirst()
                .orElse(null);
    }

    private record SourceDomain(EvidenceSource source, EvidenceDomain domain) { }

    private static boolean isEmptyCollection(Object payload) {
        return payload instanceof Collection<?> collection && collection.isEmpty();
    }

    private ProviderEvidence result(
            EvidenceRoute route, EvidenceFetchStatus status, Object payload, String detail) {
        return new ProviderEvidence(route.domain(), route.source(), route.role(), status,
                clock.instant(), payload, detail);
    }
}
