package org.predictiveedge.decision.infrastructure;

@FunctionalInterface
public interface OpenAiResponsesTransport {
    String createResponse(String apiKey, String requestJson);
}
