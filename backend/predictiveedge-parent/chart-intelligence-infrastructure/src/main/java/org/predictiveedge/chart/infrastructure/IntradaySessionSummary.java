package org.predictiveedge.chart.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.predictiveedge.broker.domain.Candle;
import org.predictiveedge.chart.domain.MarketScreenTechnicalAssessment;

/**
 * Compact intraday view of one stock for the AI prompt. It holds every completed five-minute bar of the cutoff's
 * session, the session VWAP and levels, volume against the same time on earlier sessions, the trend values from the
 * deterministic analysis, and daily context. Only bars that were complete at the cutoff are used. The full history
 * stays in code and is not sent to the model.
 */
final class IntradaySessionSummary {
    static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Duration BAR = Duration.ofMinutes(5);
    private static final LocalTime OPENING_RANGE_END = LocalTime.of(9, 30);
    private static final int PRIOR_SESSIONS = 20;

    private IntradaySessionSummary() { }

    static ObjectNode build(ObjectMapper json, List<Candle> candles, Instant cutoff,
            MarketScreenTechnicalAssessment technical) {
        Objects.requireNonNull(cutoff, "Summary cutoff is required");
        var completed = candles.stream()
                .filter(candle -> !candle.timestamp().plus(BAR).isAfter(cutoff))
                .sorted(Comparator.comparing(Candle::timestamp))
                .toList();
        LocalDate session = LocalDate.ofInstant(cutoff, MARKET_ZONE);
        var today = completed.stream().filter(candle -> date(candle).equals(session)).toList();
        var prior = completed.stream().filter(candle -> date(candle).isBefore(session))
                .collect(Collectors.groupingBy(IntradaySessionSummary::date, TreeMap::new, Collectors.toList()));
        LocalTime sameTimeLimit = ZonedDateTime.ofInstant(cutoff, MARKET_ZONE).toLocalTime().minus(BAR);
        var recent = prior.descendingMap().values().stream().limit(PRIOR_SESSIONS).toList();

        ObjectNode root = json.createObjectNode();
        root.put("session_date", session.toString());
        root.put("bar_interval", "5m");
        root.put("bar_format", "[start time IST, open, high, low, close, volume]");
        root.put("completed_bars", today.size());
        ArrayNode bars = root.putArray("bars");
        for (Candle candle : today) {
            bars.addArray().add(time(candle).toString()).add(price(candle.open())).add(price(candle.high()))
                    .add(price(candle.low())).add(price(candle.close())).add(candle.volume());
        }

        long totalVolume = today.stream().mapToLong(Candle::volume).sum();
        BigDecimal priceVolume = BigDecimal.ZERO;
        for (Candle candle : today) {
            priceVolume = priceVolume.add(typical(candle).multiply(BigDecimal.valueOf(candle.volume())));
        }
        putDecimal(root, "vwap", totalVolume == 0 ? null
                : priceVolume.divide(BigDecimal.valueOf(totalVolume), 2, RoundingMode.HALF_UP));
        putDecimal(root, "session_high", highOf(today));
        putDecimal(root, "session_low", lowOf(today));

        var opening = today.stream().filter(candle -> time(candle).isBefore(OPENING_RANGE_END)).toList();
        ObjectNode openingRange = root.putObject("opening_range");
        openingRange.put("bars", opening.size());
        putDecimal(openingRange, "high", highOf(opening));
        putDecimal(openingRange, "low", lowOf(opening));

        ObjectNode volume = root.putObject("volume");
        volume.put("today", totalVolume);
        var sameTimeTotals = recent.stream()
                .map(bars1 -> bars1.stream().filter(candle -> !time(candle).isAfter(sameTimeLimit))
                        .mapToLong(Candle::volume).sum())
                .toList();
        volume.put("prior_sessions_used", sameTimeTotals.size());
        BigDecimal average = sameTimeTotals.isEmpty() ? null
                : BigDecimal.valueOf(sameTimeTotals.stream().mapToLong(Long::longValue).sum())
                        .divide(BigDecimal.valueOf(sameTimeTotals.size()), 2, RoundingMode.HALF_UP);
        putDecimal(volume, "average_same_time_prior_sessions", average);
        putDecimal(volume, "ratio_to_average", average == null || average.signum() == 0 ? null
                : BigDecimal.valueOf(totalVolume).divide(average, 4, RoundingMode.HALF_UP));

        ObjectNode trend = root.putObject("trend");
        putDecimal(trend, "ema20", technical == null ? null : technical.ema20());
        putDecimal(trend, "ema50", technical == null ? null : technical.ema50());
        putDecimal(trend, "rsi14", technical == null ? null : technical.rsi14());
        putDecimal(trend, "atr14", technical == null ? null : technical.atr14());

        ObjectNode daily = root.putObject("daily_context");
        var previous = prior.isEmpty() ? null : prior.lastEntry().getValue();
        putDecimal(daily, "previous_close", previous == null ? null
                : price(previous.get(previous.size() - 1).close()));
        putDecimal(daily, "intraday_high", highOf(today));
        putDecimal(daily, "intraday_low", lowOf(today));
        putDecimal(daily, "window_high", highOf(completed));
        putDecimal(daily, "window_low", lowOf(completed));
        daily.put("window_from", completed.isEmpty() ? null : date(completed.getFirst()).toString());
        daily.put("window_to", session.toString());
        var ranges = recent.stream().map(day -> highOf(day).subtract(lowOf(day))).toList();
        putDecimal(daily, "average_daily_range_prior_sessions", ranges.isEmpty() ? null
                : ranges.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(ranges.size()), 2, RoundingMode.HALF_UP));
        return root;
    }

    private static LocalDate date(Candle candle) {
        return ZonedDateTime.ofInstant(candle.timestamp(), MARKET_ZONE).toLocalDate();
    }

    private static LocalTime time(Candle candle) {
        return ZonedDateTime.ofInstant(candle.timestamp(), MARKET_ZONE).toLocalTime();
    }

    private static BigDecimal typical(Candle candle) {
        return candle.high().add(candle.low()).add(candle.close())
                .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal highOf(List<Candle> candles) {
        return candles.stream().map(Candle::high).max(BigDecimal::compareTo)
                .map(IntradaySessionSummary::price).orElse(null);
    }

    private static BigDecimal lowOf(List<Candle> candles) {
        return candles.stream().map(Candle::low).min(BigDecimal::compareTo)
                .map(IntradaySessionSummary::price).orElse(null);
    }

    private static BigDecimal price(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static void putDecimal(ObjectNode node, String name, BigDecimal value) {
        if (value == null) node.putNull(name);
        else node.put(name, value);
    }
}
