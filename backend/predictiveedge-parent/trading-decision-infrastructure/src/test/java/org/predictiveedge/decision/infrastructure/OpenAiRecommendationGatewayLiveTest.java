package org.predictiveedge.decision.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class OpenAiRecommendationGatewayLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PE_RUN_OPENAI_LIVE_TEST", matches = "true")
    void receivesARealStructuredShadowRecommendation() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv().getOrDefault("PE_OPENAI_MODEL", "gpt-5.4-mini");
        Duration timeout = Duration.ofSeconds(60);
        var transport = new JdkOpenAiResponsesTransport(HttpClient.newBuilder().connectTimeout(timeout).build(),
                URI.create("https://api.openai.com/v1/responses"), timeout);
        var gateway = new OpenAiRecommendationGateway(OpenAiRecommendationGatewayTest.json(), transport, "openai", apiKey,
                model, OpenAiRecommendationGatewayTest.input(), Clock.systemUTC(),
                () -> UUID.randomUUID().toString());

        var recommendation = gateway.recommend(OpenAiRecommendationGatewayTest.bundle(
                org.predictiveedge.decision.domain.AssessmentReadiness.READY));

        assertThat(recommendation.bundleId()).isEqualTo("bundle-1");
        assertThat(recommendation.rationale()).isNotBlank();
        assertThat(recommendation.evidenceReferences()).isNotNull();
    }
}
