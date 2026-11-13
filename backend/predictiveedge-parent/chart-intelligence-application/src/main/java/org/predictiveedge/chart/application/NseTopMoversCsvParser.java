package org.predictiveedge.chart.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Strict adapter for NSE Top 20 Gainers/Losers CSV exports. */
public final class NseTopMoversCsvParser implements MarketScreenCsvParser {
    private static final List<String> REQUIRED = List.of(
            "SYMBOL", "OPEN", "HIGH", "LOW", "PREV. CLOSE", "LTP", "%CHANGE", "VOLUME", "VALUE");

    @Override
    public Set<MarketScreenType> supportedTypes() {
        return Set.of(MarketScreenType.TOP_GAINERS, MarketScreenType.TOP_LOSERS);
    }

    @Override
    public ParseResult parse(MarketScreenType type, String csv, Instant observedAt) {
        if (!supportedTypes().contains(type)) throw new IllegalArgumentException("Unsupported top-movers screen type");
        if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Market-screen CSV is empty");
        Objects.requireNonNull(observedAt, "Observation timestamp is required");
        List<List<String>> rows = rows(csv);
        if (rows.size() < 2) throw new IllegalArgumentException("Market-screen CSV contains no data rows");
        Map<String, Integer> columns = indexes(rows.getFirst());
        REQUIRED.forEach(name -> {
            if (!columns.containsKey(name)) throw new IllegalArgumentException("Market-screen CSV is missing " + name);
        });
        int width = rows.getFirst().size();
        var accepted = new ArrayList<MarketScreenObservation>();
        var rejected = new ArrayList<RowRejection>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            String symbol = row.isEmpty() ? "ROW_" + (index + 1) : row.getFirst().trim();
            try {
                if (row.size() != width) throw new IllegalArgumentException("invalid column count");
                BigDecimal change = decimal(row, columns, "%CHANGE");
                if (type == MarketScreenType.TOP_GAINERS && change.signum() <= 0)
                    throw new IllegalArgumentException("%CHANGE must be positive for TOP_GAINERS");
                if (type == MarketScreenType.TOP_LOSERS && change.signum() >= 0)
                    throw new IllegalArgumentException("%CHANGE must be negative for TOP_LOSERS");
                accepted.add(new MarketScreenObservation(type, "NSE", cell(row, columns, "SYMBOL"), "EQ",
                        index, decimal(row, columns, "OPEN"), decimal(row, columns, "HIGH"),
                        decimal(row, columns, "LOW"), decimal(row, columns, "PREV. CLOSE"),
                        decimal(row, columns, "LTP"), change, whole(row, columns, "VOLUME"),
                        decimal(row, columns, "VALUE"), optional(row, columns, "CA"), observedAt));
            } catch (RuntimeException invalid) {
                rejected.add(new RowRejection(index + 1, symbol,
                        invalid.getMessage() == null ? invalid.getClass().getSimpleName() : invalid.getMessage()));
            }
        }
        if (accepted.isEmpty()) throw new IllegalArgumentException("Market-screen CSV contains no usable rows");
        return new ParseResult(accepted, rejected);
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String name) {
        String value = row.get(columns.get(name)).trim();
        if (value.isEmpty() || "-".equals(value)) throw new IllegalArgumentException(name + " is unavailable");
        return value;
    }

    private static String optional(List<String> row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null) return "";
        String value = row.get(index).trim();
        return "-".equals(value) ? "" : value;
    }

    private static BigDecimal decimal(List<String> row, Map<String, Integer> columns, String name) {
        try { return new BigDecimal(cell(row, columns, name).replace(",", "")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " is not numeric"); }
    }

    private static long whole(List<String> row, Map<String, Integer> columns, String name) {
        try { return Long.parseLong(cell(row, columns, name).replace(",", "")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException(name + " is not a whole number"); }
    }

    private static Map<String, Integer> indexes(List<String> header) {
        var result = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < header.size(); index++)
            result.put(header.get(index).replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT), index);
        return result;
    }

    private static List<List<String>> rows(String csv) {
        var result = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int index = 0; index < csv.length(); index++) {
            char value = csv.charAt(index);
            if (index == 0 && value == '\uFEFF') continue;
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') { field.append('"'); index++; }
                    else { quoted = false; closedQuote = true; }
                } else field.append(value);
            } else if (value == '"') {
                if (!field.toString().isBlank())
                    throw new IllegalArgumentException("Market-screen CSV contains a misplaced quote");
                field.setLength(0);
                quoted = true;
            } else if (value == ',') {
                row.add(field.toString()); field.setLength(0); closedQuote = false;
            } else if (value == '\r' || value == '\n') {
                if (value == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') index++;
                row.add(field.toString()); field.setLength(0);
                if (row.stream().anyMatch(cell -> !cell.isBlank())) result.add(List.copyOf(row));
                row.clear(); closedQuote = false;
            } else if (closedQuote) {
                if (!Character.isWhitespace(value))
                    throw new IllegalArgumentException("Market-screen CSV contains characters after a closing quote");
            } else field.append(value);
        }
        if (quoted) throw new IllegalArgumentException("Market-screen CSV contains an unterminated quote");
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            if (row.stream().anyMatch(cell -> !cell.isBlank())) result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }
}
