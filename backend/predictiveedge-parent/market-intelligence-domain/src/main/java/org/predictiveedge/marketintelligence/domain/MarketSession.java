package org.predictiveedge.marketintelligence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Versioned venue session used to align intraday facts without assuming regular hours. */
public record MarketSession(
        MarketSessionId id,
        Instant barAnchor,
        Instant sessionEnd,
        List<SessionPhaseWindow> phaseWindows,
        String calendarVersion) {

    public MarketSession {
        Objects.requireNonNull(id, "Market session id is required");
        Objects.requireNonNull(barAnchor, "Bar anchor is required");
        Objects.requireNonNull(sessionEnd, "Session end is required");
        if (!barAnchor.isBefore(sessionEnd)) {
            throw new IllegalArgumentException("Bar anchor must precede session end");
        }
        if (calendarVersion == null || calendarVersion.isBlank()) {
            throw new IllegalArgumentException("Calendar version is required");
        }
        calendarVersion = calendarVersion.trim();
        phaseWindows = List.copyOf(Objects.requireNonNull(phaseWindows, "Phase windows are required"));
        if (phaseWindows.isEmpty()) {
            throw new IllegalArgumentException("At least one phase window is required");
        }
        var ordered = phaseWindows.stream().sorted(Comparator.comparing(SessionPhaseWindow::startsAt)).toList();
        if (!ordered.equals(phaseWindows)) {
            throw new IllegalArgumentException("Phase windows must be ordered by start time");
        }
        for (int index = 1; index < phaseWindows.size(); index++) {
            if (phaseWindows.get(index).startsAt().isBefore(phaseWindows.get(index - 1).endsAt())) {
                throw new IllegalArgumentException("Phase windows must not overlap");
            }
        }
    }

    public Optional<MarketSessionPhase> phaseAt(Instant instant) {
        return phaseWindows.stream().filter(window -> window.contains(instant))
                .map(SessionPhaseWindow::phase).findFirst();
    }

    /** Returns a bar only for a timestamp that occurred during continuous trading. */
    public Optional<BarInterval> barIntervalAt(Instant instant, BarTimeframe timeframe) {
        Objects.requireNonNull(instant, "Instant is required");
        Objects.requireNonNull(timeframe, "Bar timeframe is required");
        if (phaseAt(instant).orElse(null) != MarketSessionPhase.CONTINUOUS) {
            return Optional.empty();
        }
        Duration elapsed = Duration.between(barAnchor, instant);
        if (elapsed.isNegative()) {
            return Optional.empty();
        }
        long intervalNanos = timeframe.duration().toNanos();
        long offset = elapsed.toNanos() / intervalNanos;
        Instant startsAt = barAnchor.plusNanos(Math.multiplyExact(offset, intervalNanos));
        Instant naturalEnd = startsAt.plus(timeframe.duration());
        Instant endsAt = naturalEnd.isAfter(sessionEnd) ? sessionEnd : naturalEnd;
        return Optional.of(new BarInterval(startsAt, endsAt, naturalEnd.isAfter(sessionEnd)));
    }
}
