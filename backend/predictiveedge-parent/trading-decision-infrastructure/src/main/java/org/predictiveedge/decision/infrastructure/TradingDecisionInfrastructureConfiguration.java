package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.predictiveedge.chart.application.ChartSnapshotQueryPort;
import org.predictiveedge.marketintelligence.application.MarketContextQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class TradingDecisionInfrastructureConfiguration {
    @Bean
    JdbcShadowDecisionStore jdbcShadowDecisionStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcShadowDecisionStore(jdbc, json);
    }

    @Bean
    JdbcShadowEvidenceStore jdbcShadowEvidenceStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcShadowEvidenceStore(jdbc, json, () -> UUID.randomUUID().toString());
    }

    @Bean
    MarketContextDecisionResourceQuery marketContextDecisionResourceQuery(
            MarketContextQueryPort contexts,
            @Value("${predictiveedge.shadow-decision.market-context-horizon:INTRADAY}") String horizon) {
        return new MarketContextDecisionResourceQuery(contexts, horizon);
    }

    @Bean
    ChartDecisionResourceQuery chartDecisionResourceQuery(ChartSnapshotQueryPort snapshots) {
        return new ChartDecisionResourceQuery(snapshots);
    }
}
