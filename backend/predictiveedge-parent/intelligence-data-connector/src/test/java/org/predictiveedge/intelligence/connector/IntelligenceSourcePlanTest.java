package org.predictiveedge.intelligence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.predictiveedge.decision.domain.DecisionResourceType;

class IntelligenceSourcePlanTest {
    @Test
    void everyEvidenceDomainHasAnOrderedSourcePlan() {
        for (EvidenceDomain domain : EvidenceDomain.values()) {
            assertThat(IntelligenceSourcePlan.routesFor(domain)).isNotEmpty()
                    .extracting(EvidenceRoute::priority).isSorted();
        }
    }

    @Test
    void everyOneOfTheTwelveDecisionResourcesHasEvidenceDomains() {
        assertThat(DecisionResourceType.values()).hasSize(12);
        for (DecisionResourceType resourceType : DecisionResourceType.values()) {
            assertThat(IntelligenceSourcePlan.domainsFor(resourceType)).isNotEmpty();
        }
    }

    @Test
    void authoritativeAndSupplementaryMappingsMatchTheRequestedPolicy() {
        assertThat(IntelligenceSourcePlan.routesFor(EvidenceDomain.CORPORATE_ACTIONS))
                .extracting(EvidenceRoute::source, EvidenceRoute::role)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(EvidenceSource.NSE, SourceRole.PRIMARY),
                        org.assertj.core.groups.Tuple.tuple(EvidenceSource.TRENDLYNE, SourceRole.SUPPLEMENTARY));
        assertThat(IntelligenceSourcePlan.routesFor(EvidenceDomain.HISTORICAL_AND_PREDICTIVE))
                .extracting(EvidenceRoute::source)
                .containsExactly(EvidenceSource.ZERODHA, EvidenceSource.TRENDLYNE);
    }

    @Test
    void nseEquityNewsPlanExcludesCoinDesk() {
        assertThat(IntelligenceSourcePlan.routesFor(EvidenceDomain.NEWS_AND_SENTIMENT))
                .extracting(EvidenceRoute::source)
                .doesNotContain(EvidenceSource.COINDESK);
    }
}
