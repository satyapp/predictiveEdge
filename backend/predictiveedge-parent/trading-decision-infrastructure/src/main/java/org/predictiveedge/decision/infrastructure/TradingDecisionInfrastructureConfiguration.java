package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
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
}
