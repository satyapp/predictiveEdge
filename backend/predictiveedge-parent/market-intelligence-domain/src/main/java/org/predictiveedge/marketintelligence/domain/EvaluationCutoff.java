package org.predictiveedge.marketintelligence.domain;

import java.time.Instant;
import java.util.Objects;

/** The two independent time boundaries used by a causal analytical run. */
public record EvaluationCutoff(Instant analysisCutoff, Instant knowledgeCutoff) {
    public EvaluationCutoff {
        Objects.requireNonNull(analysisCutoff, "Analysis cutoff is required");
        Objects.requireNonNull(knowledgeCutoff, "Knowledge cutoff is required");
    }
}
