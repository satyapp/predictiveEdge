package org.predictiveedge.marketintelligence.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Point-in-time synchronized constituent change used by advance/decline breadth. */
public record BreadthConstituent(ObservationSubject subject, BigDecimal priorClose, BigDecimal currentClose,
                                 Instant eventTime, Instant availableAt) {
    public BreadthConstituent {
        Objects.requireNonNull(subject); Objects.requireNonNull(priorClose); Objects.requireNonNull(currentClose);
        Objects.requireNonNull(eventTime); Objects.requireNonNull(availableAt);
        if (subject.type() != ObservationSubjectType.INSTRUMENT || priorClose.signum() <= 0 || currentClose.signum() <= 0)
            throw new IllegalArgumentException("Breadth constituent values are invalid");
        if (availableAt.isBefore(eventTime)) throw new IllegalArgumentException("Breadth availability precedes event time");
    }
}
