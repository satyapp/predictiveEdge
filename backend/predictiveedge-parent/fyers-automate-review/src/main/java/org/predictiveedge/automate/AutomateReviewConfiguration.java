package org.predictiveedge.automate;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.predictiveedge.broker.connection.CredentialCipher;
import org.predictiveedge.decision.infrastructure.JdkOpenAiResponsesTransport;
import org.predictiveedge.decision.infrastructure.OllamaResponsesTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "predictiveedge.fyers-automate", name = "enabled", havingValue = "true")
public class AutomateReviewConfiguration {
    @Bean JdbcAutomateReviewStore automateReviewStore(JdbcTemplate jdbc) { return new JdbcAutomateReviewStore(jdbc); }

    @Bean ReconciliationEngine automateReconciliationEngine(ObjectMapper json,
            @Value("${predictiveedge.ai.provider:none}") String provider,
            @Value("${predictiveedge.ai.openai.api-key:}") String openAiKey,
            @Value("${predictiveedge.ai.openai.model:gpt-5.4-mini}") String openAiModel,
            @Value("${predictiveedge.ai.openai.endpoint:https://api.openai.com/v1/responses}") URI openAiEndpoint,
            @Value("${predictiveedge.ai.openai.timeout-seconds:120}") long openAiTimeout,
            @Value("${predictiveedge.ai.ollama.model:qwen3:8b}") String ollamaModel,
            @Value("${predictiveedge.ai.ollama.endpoint:http://localhost:11434/api/chat}") URI ollamaEndpoint,
            @Value("${predictiveedge.ai.ollama.timeout-seconds:360}") long ollamaTimeout,
            @Value("${predictiveedge.ai.ollama.context-window-tokens:8192}") int contextWindow,
            @Value("${predictiveedge.ai.ollama.maximum-output-tokens:1024}") int maximumOutput) {
        return switch (provider.toLowerCase(java.util.Locale.ROOT)) {
            case "openai" -> openAiKey.isBlank() ? new DeterministicReconciliationEngine()
                    : new AiReconciliationEngine(json, new JdkOpenAiResponsesTransport(
                            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), openAiEndpoint,
                            Duration.ofSeconds(openAiTimeout)), "openai", openAiKey, openAiModel);
            case "ollama" -> new AiReconciliationEngine(json, new OllamaResponsesTransport(json,
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), ollamaEndpoint,
                    Duration.ofSeconds(ollamaTimeout), contextWindow, maximumOutput), "ollama", "local-ollama", ollamaModel);
            default -> new DeterministicReconciliationEngine();
        };
    }

    @Bean AutomateReviewService automateReviewService(JdbcAutomateReviewStore store, CredentialCipher cipher,
            ReconciliationEngine reconciliation, Optional<GuardianAdvisor> guardian, ObjectMapper json,
            @Value("${predictiveedge.fyers-automate.allowed-hosts:fyers.in}") String allowedHosts,
            @Value("${predictiveedge.fyers-automate.minimum-interval-seconds:60}") long minimumInterval,
            @Value("${predictiveedge.fyers-automate.timeout-seconds:15}") long timeout,
            @Value("${predictiveedge.broker.web-base-url:http://localhost:3000/}") String webBaseUrl) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new AutomateReviewService(store, cipher, new JdkAutomateWebhookSender(http, Duration.ofSeconds(timeout)),
                reconciliation, guardian.orElse(GuardianAdvisor.NONE), new AutomatePayloads(json, webBaseUrl),
                new WebhookUrlPolicy(Arrays.asList(allowedHosts.split(","))), Duration.ofSeconds(minimumInterval),
                Clock.systemUTC(), UUID::randomUUID);
    }
}
