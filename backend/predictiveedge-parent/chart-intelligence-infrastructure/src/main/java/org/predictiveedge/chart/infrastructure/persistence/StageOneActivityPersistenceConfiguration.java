package org.predictiveedge.chart.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Registers the Stage-1 persistence store and the entities it maps to the market_intelligence schema. */
@Configuration(proxyBeanMethods = false)
@EntityScan(basePackages = "org.predictiveedge.chart.infrastructure.persistence")
public class StageOneActivityPersistenceConfiguration {
    @Bean
    StageOneActivityStore stageOneActivityStore(EntityManagerFactory entityManagers,
            PlatformTransactionManager transactionManager, ObjectMapper json) {
        return new JpaStageOneActivityStore(entityManagers, transactionManager, json);
    }
}
