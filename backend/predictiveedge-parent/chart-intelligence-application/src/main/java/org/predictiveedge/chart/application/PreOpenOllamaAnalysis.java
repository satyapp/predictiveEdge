package org.predictiveedge.chart.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Preliminary local-model interpretation; never a final trading recommendation. */
public record PreOpenOllamaAnalysis(
        String model,
        Instant generatedAt,
        boolean invoked,
        String marketTone,
        String overview,
        List<CandidateInterpretation> candidates,
        List<String> cautions) {

    public PreOpenOllamaAnalysis {
        model = required(model, "Model");
        Objects.requireNonNull(generatedAt, "Generated-at timestamp is required");
        marketTone = required(marketTone, "Market tone");
        overview = required(overview, "Overview");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "Candidates are required"));
        cautions = strings(cautions, "Cautions");
    }

    public record CandidateInterpretation(
            String symbol, String stance, String thesis, String confirmation, String risk) {
        public CandidateInterpretation {
            symbol = required(symbol, "Symbol").toUpperCase(java.util.Locale.ROOT);
            stance = required(stance, "Stance");
            thesis = required(thesis, "Thesis");
            confirmation = required(confirmation, "Confirmation");
            risk = required(risk, "Risk");
        }
    }

    private static List<String> strings(List<String> values, String label) {
        Objects.requireNonNull(values, label + " are required");
        if (values.stream().anyMatch(value -> value == null || value.isBlank()))
            throw new IllegalArgumentException(label + " cannot contain blanks");
        return values.stream().map(String::trim).toList();
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
        return value.trim();
    }
}
