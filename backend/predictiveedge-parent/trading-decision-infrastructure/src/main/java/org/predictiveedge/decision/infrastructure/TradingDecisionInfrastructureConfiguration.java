package org.predictiveedge.decision.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.math.BigDecimal;
import org.predictiveedge.broker.connection.BrokerAccountEvidencePort;
import org.predictiveedge.decision.application.ExecutionAssessmentPolicy;
import org.predictiveedge.decision.application.PersonalPortfolioPolicy;
import org.predictiveedge.decision.application.PersonalRiskPolicy;
import org.predictiveedge.decision.application.SafetySnapshotProducer;
import org.predictiveedge.marketintelligence.application.MarketDepthQueryPort;
import org.predictiveedge.chart.application.ChartSnapshotQueryPort;
import org.predictiveedge.decision.application.AiEvidencePayloadQuery;
import org.predictiveedge.decision.application.AiRecommendationGateway;
import org.predictiveedge.marketintelligence.application.MarketContextQueryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    JdbcDecisionSafetySnapshotStore jdbcDecisionSafetySnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        return new JdbcDecisionSafetySnapshotStore(jdbc, json);
    }

    @Bean
    @ConditionalOnProperty(prefix = "predictiveedge.safety-assessment", name = "enabled", havingValue = "true")
    SafetySnapshotProducer safetySnapshotProducer(
            MarketDepthQueryPort depths,
            BrokerAccountEvidencePort accounts,
            JdbcDecisionSafetySnapshotStore snapshots,
            @Value("${predictiveedge.safety-assessment.execution.policy-version:execution-v1}") String executionVersion,
            @Value("${predictiveedge.safety-assessment.execution.maximum-depth-age:PT3S}") Duration maximumDepthAge,
            @Value("${predictiveedge.safety-assessment.execution.validity:PT2S}") Duration executionValidity,
            @Value("${predictiveedge.safety-assessment.execution.maximum-spread-bps}") BigDecimal maximumSpreadBps,
            @Value("${predictiveedge.safety-assessment.execution.maximum-slippage-bps}") BigDecimal maximumSlippageBps,
            @Value("${predictiveedge.safety-assessment.execution.fees-and-taxes-bps}") BigDecimal feesAndTaxesBps,
            @Value("${predictiveedge.safety-assessment.execution.decision-to-order-latency-millis:0}") long latencyMillis,
            @Value("${predictiveedge.safety-assessment.risk.policy-version}") String riskVersion,
            @Value("${predictiveedge.safety-assessment.risk.maximum-risk-per-trade}") BigDecimal maximumRiskPerTrade,
            @Value("${predictiveedge.safety-assessment.risk.daily-loss-limit}") BigDecimal dailyLossLimit,
            @Value("${predictiveedge.safety-assessment.risk.maximum-open-risk}") BigDecimal maximumOpenRisk,
            @Value("${predictiveedge.safety-assessment.risk.maximum-position-value}") BigDecimal maximumPositionValue,
            @Value("${predictiveedge.safety-assessment.risk.maximum-account-age:PT5S}") Duration riskAccountAge,
            @Value("${predictiveedge.safety-assessment.risk.validity:PT2S}") Duration riskValidity,
            @Value("${predictiveedge.safety-assessment.portfolio.policy-version}") String portfolioVersion,
            @Value("${predictiveedge.safety-assessment.portfolio.maximum-single-position-weight-percent}") BigDecimal maximumPositionWeight,
            @Value("${predictiveedge.safety-assessment.portfolio.allow-short-positions:false}") boolean allowShortPositions,
            @Value("${predictiveedge.safety-assessment.portfolio.maximum-account-age:PT5S}") Duration portfolioAccountAge,
            @Value("${predictiveedge.safety-assessment.portfolio.validity:PT2S}") Duration portfolioValidity) {
        var execution = new ExecutionAssessmentPolicy(executionVersion, maximumDepthAge, executionValidity,
                maximumSpreadBps, maximumSlippageBps, feesAndTaxesBps, latencyMillis);
        var risk = new PersonalRiskPolicy(riskVersion, maximumRiskPerTrade, dailyLossLimit,
                maximumOpenRisk, maximumPositionValue, riskAccountAge, riskValidity);
        var portfolio = new PersonalPortfolioPolicy(portfolioVersion, maximumPositionWeight,
                allowShortPositions, portfolioAccountAge, portfolioValidity);
        return new SafetySnapshotProducer(depths, accounts, snapshots, snapshots, snapshots,
                execution, risk, portfolio, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    @Bean
    JdbcAiEvidencePayloadStore jdbcAiEvidencePayloadStore(JdbcTemplate jdbc) {
        return new JdbcAiEvidencePayloadStore(jdbc);
    }

    @Bean
    ExactAiPayloadPublisher exactAiPayloadPublisher(ObjectMapper json, JdbcAiEvidencePayloadStore payloads) {
        return new ExactAiPayloadPublisher(json, payloads);
    }

    @Bean
    MarketContextDecisionResourceQuery marketContextDecisionResourceQuery(
            MarketContextQueryPort contexts,
            ExactAiPayloadPublisher payloads,
            @Value("${predictiveedge.shadow-decision.market-context-horizon:INTRADAY}") String horizon) {
        return new MarketContextDecisionResourceQuery(contexts, horizon, payloads);
    }

    @Bean
    ChartDecisionResourceQuery chartDecisionResourceQuery(
            ChartSnapshotQueryPort snapshots, ExactAiPayloadPublisher payloads) {
        return new ChartDecisionResourceQuery(snapshots, payloads);
    }

    @Bean
    RiskDecisionResourceQuery riskDecisionResourceQuery(
            JdbcDecisionSafetySnapshotStore snapshots, ExactAiPayloadPublisher payloads) {
        return new RiskDecisionResourceQuery(snapshots, payloads);
    }

    @Bean
    PortfolioDecisionResourceQuery portfolioDecisionResourceQuery(
            JdbcDecisionSafetySnapshotStore snapshots, ExactAiPayloadPublisher payloads) {
        return new PortfolioDecisionResourceQuery(snapshots, payloads);
    }

    @Bean
    ExecutionDecisionResourceQuery executionDecisionResourceQuery(
            JdbcDecisionSafetySnapshotStore snapshots, ExactAiPayloadPublisher payloads) {
        return new ExecutionDecisionResourceQuery(snapshots, payloads);
    }

    @Bean
    @ConditionalOnProperty(prefix = "predictiveedge.ai", name = "provider", havingValue = "openai")
    OpenAiRecommendationGateway openAiRecommendationGateway(
            ObjectMapper json,
            @Value("${predictiveedge.ai.openai.api-key:}") String apiKey,
            @Value("${predictiveedge.ai.openai.model:gpt-5.4-mini}") String model,
            @Value("${predictiveedge.ai.openai.endpoint:https://api.openai.com/v1/responses}") URI endpoint,
            @Value("${predictiveedge.ai.openai.timeout-seconds:45}") long timeoutSeconds,
            AiEvidencePayloadQuery evidencePayloadQuery) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        var http = HttpClient.newBuilder().connectTimeout(timeout).build();
        return new OpenAiRecommendationGateway(json, new JdkOpenAiResponsesTransport(http, endpoint, timeout),
                "openai", apiKey, model, evidencePayloadQuery, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    @Bean
    @ConditionalOnProperty(prefix = "predictiveedge.ai", name = "provider", havingValue = "ollama")
    AiRecommendationGateway ollamaRecommendationGateway(
            ObjectMapper json,
            @Value("${predictiveedge.ai.ollama.model:qwen3:8b}") String model,
            @Value("${predictiveedge.ai.ollama.endpoint:http://localhost:11434/api/chat}") URI endpoint,
            @Value("${predictiveedge.ai.ollama.timeout-seconds:360}") long timeoutSeconds,
            @Value("${predictiveedge.ai.ollama.context-window-tokens:32768}") int contextWindowTokens,
            @Value("${predictiveedge.ai.ollama.maximum-output-tokens:1024}") int maximumOutputTokens,
            AiEvidencePayloadQuery evidencePayloadQuery) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return new OpenAiRecommendationGateway(json, new OllamaResponsesTransport(json, http, endpoint, timeout,
                contextWindowTokens, maximumOutputTokens),
                "ollama", "local-ollama", model, evidencePayloadQuery, Clock.systemUTC(),
                () -> UUID.randomUUID().toString());
    }
}
