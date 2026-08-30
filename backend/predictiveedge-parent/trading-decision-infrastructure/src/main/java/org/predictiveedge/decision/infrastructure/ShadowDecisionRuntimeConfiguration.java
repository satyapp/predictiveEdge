package org.predictiveedge.decision.infrastructure;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.decision.application.AiRecommendationGateway;
import org.predictiveedge.decision.application.ShadowDecisionCaseStore;
import org.predictiveedge.decision.application.ShadowDecisionInputQuery;
import org.predictiveedge.decision.application.ShadowDecisionService;
import org.predictiveedge.decision.application.ShadowOutcomeService;
import org.predictiveedge.decision.application.ShadowOutcomeStore;
import org.predictiveedge.decision.application.ShadowRecommendationValidator;
import org.predictiveedge.decision.domain.InstrumentRef;
import org.predictiveedge.decision.domain.RecommendationOutcomeResolver;
import org.predictiveedge.decision.domain.ShadowScope;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Activates the shadow runtime only after an AI gateway is deliberately supplied. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ShadowDecisionProperties.class)
@ConditionalOnProperty(prefix = "predictiveedge.shadow-decision", name = "enabled", havingValue = "true")
@ConditionalOnBean(AiRecommendationGateway.class)
public class ShadowDecisionRuntimeConfiguration {
    @Bean
    ShadowScope shadowScope(ShadowDecisionProperties properties) {
        return new ShadowScope(Objects.requireNonNull(properties.getUserId(), "Shadow user id is required"),
                new InstrumentRef(required(properties.getVenue(), "Shadow venue"),
                        required(properties.getInstrumentId(), "Shadow instrument id")));
    }

    @Bean
    ShadowRecommendationValidator shadowRecommendationValidator(ShadowDecisionProperties properties) {
        return new ShadowRecommendationValidator(Objects.requireNonNull(
                properties.getMinimumDirectionalProbability(), "Minimum probability is required"));
    }

    @Bean
    ShadowDecisionService shadowDecisionService(ShadowScope scope, ShadowDecisionInputQuery inputQuery,
            AiRecommendationGateway aiGateway, ShadowRecommendationValidator validator,
            ShadowDecisionCaseStore caseStore) {
        return new ShadowDecisionService(scope, inputQuery, aiGateway, validator, caseStore,
                Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    @Bean
    ShadowOutcomeService shadowOutcomeService(ShadowOutcomeStore outcomeStore) {
        return new ShadowOutcomeService(new RecommendationOutcomeResolver(), outcomeStore);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }
}
