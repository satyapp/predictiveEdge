package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class OllamaRecommendationGatewayLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PE_RUN_OLLAMA_LIVE_TEST", matches = "true")
    void receivesARealLocalStructuredShadowRecommendation() {
        var json = OpenAiRecommendationGatewayTest.json();
        String model = System.getenv().getOrDefault("PE_OLLAMA_MODEL", "qwen3:8b");
        Duration timeout = Duration.ofMinutes(6);
        var transport = new OllamaResponsesTransport(json,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                URI.create("http://localhost:11434/api/chat"), timeout, 4096, 768);
        var gateway = new OpenAiRecommendationGateway(json, transport, "ollama", "local-ollama", model,
                OpenAiRecommendationGatewayTest.input(), Clock.systemUTC(),
                () -> UUID.randomUUID().toString());

        var recommendation = gateway.recommend(OpenAiRecommendationGatewayTest.bundle(
                org.predictiveedge.decision.domain.AssessmentReadiness.READY));

        assertThat(recommendation.bundleId()).isEqualTo("bundle-1");
        assertThat(recommendation.rationale()).isNotBlank();
        assertThat(recommendation.evidenceReferences()).isNotNull();
    }
}
