package org.predictiveedge.platform.eventing.application;

/** Result of one bounded outbox dispatch cycle. */
public record DispatchSummary(int claimed, int published, int failed) {
    public DispatchSummary {
        if (claimed < 0 || published < 0 || failed < 0 || published + failed != claimed) {
            throw new IllegalArgumentException("Dispatch summary counts are inconsistent");
        }
    }
}
