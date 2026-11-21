package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
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
    @Test
    void capturesEveryDomainAndAllTwelveModulesOncePerDistinctSymbol() {
        var connector = new RecordingConnector();
        var result = new StageOneIntelligenceEvidenceCollector(connector, new ObjectMapper())
                .collect(List.of("INFY", "INFY"), Instant.parse("2026-11-14T06:00:00Z"));

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

    private static final class RecordingConnector implements IntelligenceDataConnector {
        private final List<String> calls = new ArrayList<>();

        @Override
        public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
            calls.add(query.symbol() + ":" + domain);
            return List.of(new ProviderEvidence(domain, EvidenceSource.TRENDLYNE, SourceRole.SUPPLEMENTARY,
                    EvidenceFetchStatus.SUCCESS, query.cutoff(), Map.of("kind", domain.name()), ""));
        }

        @Override
        public ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query) {
            throw new UnsupportedOperationException();
        }
    }
}
