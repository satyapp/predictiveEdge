package org.predictiveedge.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import org.predictiveedge.chart.application.AnalysisRunHistoryStore;
import org.predictiveedge.decision.infrastructure.JdkOpenAiResponsesTransport;
import org.predictiveedge.decision.infrastructure.OllamaResponsesTransport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StockPipelineConfiguration {
    @Bean QualifiedStockService qualifiedStockService(AnalysisRunHistoryStore runs, ObjectMapper json) {
        return new QualifiedStockService(runs, json);
    }

    @Bean LevelTwoAgent levelTwoAgent(ObjectMapper json, AnalysisRunHistoryStore runs,
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
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "openai" -> openAiKey.isBlank() ? new LevelTwoAgent(json, null, "", "not configured", runs, Clock.systemUTC())
                    : new LevelTwoAgent(json, new JdkOpenAiResponsesTransport(http, openAiEndpoint, Duration.ofSeconds(openAiTimeout)),
                            openAiKey, openAiModel, runs, Clock.systemUTC());
            case "ollama" -> new LevelTwoAgent(json, new OllamaResponsesTransport(json, http, ollamaEndpoint,
                    Duration.ofSeconds(ollamaTimeout), contextWindow, Math.max(maximumOutput, 1024)), "local-ollama", ollamaModel,
                    runs, Clock.systemUTC());
            default -> new LevelTwoAgent(json, null, "", "not configured", runs, Clock.systemUTC());
        };
    }
}
