package org.predictiveedge.platform.eventing.infrastructure;

import java.time.Clock;

import org.predictiveedge.platform.eventing.application.IdempotentEventConsumer;
import org.predictiveedge.platform.eventing.application.OutboxDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(EventingDispatchProperties.class)
public class EventingInfrastructureConfiguration {
    @Bean
    JdbcEventOutbox jdbcEventOutbox(JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        return new JdbcEventOutbox(jdbc, json, clock);
    }

    @Bean
    JdbcInboxTransaction jdbcInboxTransaction(
            JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock) {
        return new JdbcInboxTransaction(jdbc, new TransactionTemplate(transactionManager), clock);
    }

    @Bean
    IdempotentEventConsumer idempotentEventConsumer(JdbcInboxTransaction inbox) {
        return new IdempotentEventConsumer(inbox);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "predictiveedge.eventing.dispatch", name = "enabled", havingValue = "true")
    KafkaEventTransport kafkaEventTransport(
            KafkaTemplate<String, String> kafka,
            ObjectMapper json,
            Clock clock,
            EventingDispatchProperties properties) {
        return new KafkaEventTransport(kafka, json, clock, properties.getPublishTimeout());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "predictiveedge.eventing.dispatch", name = "enabled", havingValue = "true")
    OutboxDispatcher outboxDispatcher(
            JdbcEventOutbox store,
            KafkaEventTransport transport,
            Clock clock,
            EventingDispatchProperties properties) {
        return new OutboxDispatcher(
                store, transport, clock, properties.getLeaseDuration(), properties.getRetryDelay());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "predictiveedge.eventing.dispatch", name = "enabled", havingValue = "true")
    EventOutboxScheduler eventOutboxScheduler(
            OutboxDispatcher dispatcher, EventingDispatchProperties properties) {
        return new EventOutboxScheduler(dispatcher, properties.getBatchSize());
    }
}
