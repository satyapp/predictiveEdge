package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.StageOneAiResult;
import org.predictiveedge.chart.application.StageOneGoldenCopy;

final class StageOneGoldenCopyFactory {
    private final ObjectMapper json;
    private final Clock clock;

    StageOneGoldenCopyFactory(ObjectMapper json, Clock clock) {
        this.json = Objects.requireNonNull(json);
        this.clock = Objects.requireNonNull(clock);
    }

    StageOneAiResult freeze(String category, String evidenceJson, PreOpenOllamaAnalysis analysis) {
        String fingerprint = sha256(evidenceJson + "\n" + write(fingerprintPayload(analysis)));
        var goldenCopy = new StageOneGoldenCopy(StageOneIntelligenceEvidenceCollector.SCHEMA_VERSION,
                category, clock.instant(), fingerprint, evidenceJson, analysis, "PLANNED_PHASE_2");
        return new StageOneAiResult(analysis, goldenCopy);
    }

    private com.fasterxml.jackson.databind.node.ObjectNode fingerprintPayload(PreOpenOllamaAnalysis analysis) {
        var root = json.createObjectNode();
        root.put("model", analysis.model());
        root.put("generated_at", analysis.generatedAt().toString());
        root.put("invoked", analysis.invoked());
        root.put("market_tone", analysis.marketTone());
        root.put("overview", analysis.overview());
        var candidates = root.putArray("candidates");
        analysis.candidates().forEach(value -> candidates.addObject()
                .put("symbol", value.symbol()).put("stance", value.stance()).put("thesis", value.thesis())
                .put("confirmation", value.confirmation()).put("risk", value.risk()));
        var cautions = root.putArray("cautions");
        analysis.cautions().forEach(cautions::add);
        return root;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stage-1 analysis could not be serialized", failure);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
