package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class ChartIntelligenceInfrastructureConfiguration {
    @Bean
    JdbcChartSnapshotStore jdbcChartSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcChartSnapshotStore(jdbc, json);
    }
}
