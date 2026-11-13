package org.predictiveedge.intelligence.connector;

import static org.predictiveedge.intelligence.connector.EvidenceDomain.*;
import static org.predictiveedge.intelligence.connector.EvidenceSource.*;
import static org.predictiveedge.intelligence.connector.SourceRole.*;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.predictiveedge.decision.domain.DecisionResourceType;

/** Immutable mapping from nine evidence domains into all twelve governed decision resources. */
public final class IntelligenceSourcePlan {
    private static final Map<EvidenceDomain, List<EvidenceRoute>> ROUTES = routes();
    private static final Map<DecisionResourceType, Set<EvidenceDomain>> RESOURCE_DOMAINS = resourceDomains();

    private IntelligenceSourcePlan() { }

    public static List<EvidenceRoute> routesFor(EvidenceDomain domain) {
        List<EvidenceRoute> routes = ROUTES.get(domain);
        if (routes == null) throw new IllegalArgumentException("Unknown evidence domain: " + domain);
        return routes;
    }

    public static Set<EvidenceDomain> domainsFor(DecisionResourceType resourceType) {
        Set<EvidenceDomain> domains = RESOURCE_DOMAINS.get(resourceType);
        if (domains == null) throw new IllegalArgumentException("Unknown decision resource type: " + resourceType);
        return domains;
    }

    private static Map<EvidenceDomain, List<EvidenceRoute>> routes() {
        var routes = new EnumMap<EvidenceDomain, List<EvidenceRoute>>(EvidenceDomain.class);
        routes.put(MARKET_DATA, List.of(route(MARKET_DATA, NSE, PRIMARY, 1),
                route(MARKET_DATA, ZERODHA, PRIMARY, 2), route(MARKET_DATA, TRENDLYNE, SUPPLEMENTARY, 3)));
        routes.put(TECHNICAL_ANALYSIS, List.of(route(TECHNICAL_ANALYSIS, RAW_DATA_ENGINE, DERIVED, 1),
                route(TECHNICAL_ANALYSIS, TRENDLYNE, SUPPLEMENTARY, 2)));
        routes.put(CHART_PATTERNS, List.of(route(CHART_PATTERNS, PATTERN_DETECTION_ENGINE, DERIVED, 1),
                route(CHART_PATTERNS, RAW_DATA_ENGINE, PRIMARY, 2)));
        routes.put(FUNDAMENTAL_ANALYSIS, List.of(route(FUNDAMENTAL_ANALYSIS, TRENDLYNE, PRIMARY, 1)));
        routes.put(NEWS_AND_SENTIMENT, List.of(route(NEWS_AND_SENTIMENT, NSE, PRIMARY, 1),
                route(NEWS_AND_SENTIMENT, TRENDLYNE, PRIMARY, 2),
                route(NEWS_AND_SENTIMENT, YAHOO_FINANCE, OPTIONAL, 3)));
        routes.put(CORPORATE_ACTIONS, List.of(route(CORPORATE_ACTIONS, NSE, PRIMARY, 1),
                route(CORPORATE_ACTIONS, TRENDLYNE, SUPPLEMENTARY, 2)));
        routes.put(MACRO_AND_ECONOMIC, List.of(route(MACRO_AND_ECONOMIC, NSE, PRIMARY, 1),
                route(MACRO_AND_ECONOMIC, ZERODHA, PRIMARY, 2),
                route(MACRO_AND_ECONOMIC, TRENDLYNE, SUPPLEMENTARY, 3)));
        routes.put(INSTITUTIONAL_FLOWS, List.of(route(INSTITUTIONAL_FLOWS, NSE, PRIMARY, 1),
                route(INSTITUTIONAL_FLOWS, TRENDLYNE, SUPPLEMENTARY, 2)));
        routes.put(HISTORICAL_AND_PREDICTIVE, List.of(route(HISTORICAL_AND_PREDICTIVE, ZERODHA, PRIMARY, 1),
                route(HISTORICAL_AND_PREDICTIVE, TRENDLYNE, SUPPLEMENTARY, 2)));
        return Map.copyOf(routes);
    }

    private static Map<DecisionResourceType, Set<EvidenceDomain>> resourceDomains() {
        var map = new EnumMap<DecisionResourceType, Set<EvidenceDomain>>(DecisionResourceType.class);
        map.put(DecisionResourceType.MARKET, Set.of(MARKET_DATA, FUNDAMENTAL_ANALYSIS, NEWS_AND_SENTIMENT,
                CORPORATE_ACTIONS, MACRO_AND_ECONOMIC, INSTITUTIONAL_FLOWS));
        map.put(DecisionResourceType.CHART, Set.of(MARKET_DATA, TECHNICAL_ANALYSIS, CHART_PATTERNS,
                HISTORICAL_AND_PREDICTIVE));
        map.put(DecisionResourceType.SCANNER, Set.of(MARKET_DATA, TECHNICAL_ANALYSIS, NEWS_AND_SENTIMENT,
                INSTITUTIONAL_FLOWS));
        map.put(DecisionResourceType.STRATEGY, Set.of(TECHNICAL_ANALYSIS, CHART_PATTERNS, FUNDAMENTAL_ANALYSIS,
                NEWS_AND_SENTIMENT, MACRO_AND_ECONOMIC, HISTORICAL_AND_PREDICTIVE));
        map.put(DecisionResourceType.RISK, Set.of(MARKET_DATA, NEWS_AND_SENTIMENT, MACRO_AND_ECONOMIC));
        map.put(DecisionResourceType.PORTFOLIO, Set.of(MARKET_DATA, FUNDAMENTAL_ANALYSIS, CORPORATE_ACTIONS,
                INSTITUTIONAL_FLOWS));
        map.put(DecisionResourceType.LEARNING, Set.of(HISTORICAL_AND_PREDICTIVE));
        map.put(DecisionResourceType.DATA_QUALITY, Set.of(EvidenceDomain.values()));
        map.put(DecisionResourceType.REGIME_DRIFT, Set.of(MARKET_DATA, TECHNICAL_ANALYSIS, MACRO_AND_ECONOMIC,
                INSTITUTIONAL_FLOWS, HISTORICAL_AND_PREDICTIVE));
        map.put(DecisionResourceType.EXECUTION, Set.of(MARKET_DATA));
        map.put(DecisionResourceType.VALIDATION, Set.of(TECHNICAL_ANALYSIS, CHART_PATTERNS,
                HISTORICAL_AND_PREDICTIVE));
        map.put(DecisionResourceType.CALIBRATION, Set.of(HISTORICAL_AND_PREDICTIVE));
        return Map.copyOf(map);
    }

    private static EvidenceRoute route(
            EvidenceDomain domain, EvidenceSource source, SourceRole role, int priority) {
        return new EvidenceRoute(domain, source, role, priority);
    }
}
