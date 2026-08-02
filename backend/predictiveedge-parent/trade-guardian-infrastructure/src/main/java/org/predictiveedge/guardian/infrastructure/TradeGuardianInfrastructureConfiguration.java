package org.predictiveedge.guardian.infrastructure;

import java.time.Clock;
import java.util.UUID;

import org.predictiveedge.guardian.application.TradeGuardianService;
import org.predictiveedge.platform.eventing.application.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class TradeGuardianInfrastructureConfiguration {
    @Bean
    JdbcTradeMonitoringCaseStore jdbcTradeMonitoringCaseStore(
            JdbcTemplate jdbc, ObjectMapper json, DomainEventPublisher events) {
        return new JdbcTradeMonitoringCaseStore(jdbc, json, events, UUID::randomUUID);
    }

    @Bean
    TradeGuardianService tradeGuardianService(JdbcTradeMonitoringCaseStore store, Clock clock) {
        return new TradeGuardianService(store, clock, UUID::randomUUID);
    }
}
