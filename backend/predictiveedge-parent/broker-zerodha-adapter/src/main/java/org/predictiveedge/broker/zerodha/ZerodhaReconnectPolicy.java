package org.predictiveedge.broker.zerodha;

import java.time.Duration;
import java.util.Objects;

/** Bounded exponential reconnect and stale-stream policy. */
public record ZerodhaReconnectPolicy(
        Duration initialDelay,
        Duration maximumDelay,
        int maximumAttempts,
        Duration staleAfter,
        int maximumFrameBytes) {

    public ZerodhaReconnectPolicy {
        Objects.requireNonNull(initialDelay); Objects.requireNonNull(maximumDelay); Objects.requireNonNull(staleAfter);
        if (initialDelay.isNegative() || maximumDelay.isNegative() || maximumDelay.compareTo(initialDelay) < 0
                || maximumAttempts < 1 || staleAfter.isZero() || staleAfter.isNegative() || maximumFrameBytes < 1024)
            throw new IllegalArgumentException("Reconnect policy is invalid");
    }

    public Duration delayForAttempt(int attempt) {
        if (attempt < 1) throw new IllegalArgumentException("Reconnect attempt must be positive");
        long multiplier = 1L << Math.min(attempt - 1, 30);
        try {
            var delay = initialDelay.multipliedBy(multiplier);
            return delay.compareTo(maximumDelay) > 0 ? maximumDelay : delay;
        } catch (ArithmeticException overflow) {
            return maximumDelay;
        }
    }
}
