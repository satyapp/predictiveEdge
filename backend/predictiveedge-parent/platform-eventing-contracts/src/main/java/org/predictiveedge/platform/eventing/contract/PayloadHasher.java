package org.predictiveedge.platform.eventing.contract;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Produces a stable SHA-256 hash from the canonical JSON representation of a payload. */
public final class PayloadHasher {
    private static final ObjectMapper CANONICAL_MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private PayloadHasher() {
    }

    public static String sha256(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Event payload is required");
        }
        try {
            byte[] canonicalJson = CANONICAL_MAPPER.writeValueAsBytes(canonicalize(payload));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonicalJson);
            return java.util.HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Event payload cannot be serialized", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static String sha256(String canonicalJson) {
        try {
            return sha256(CANONICAL_MAPPER.readTree(canonicalJson.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            throw new IllegalArgumentException("Payload is not valid JSON", exception);
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode canonical = JsonNodeFactory.instance.objectNode();
            node.propertyStream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> canonical.set(entry.getKey(), canonicalize(entry.getValue())));
            return canonical;
        }
        if (node.isArray()) {
            ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> canonical.add(canonicalize(child)));
            return canonical;
        }
        return node;
    }
}
