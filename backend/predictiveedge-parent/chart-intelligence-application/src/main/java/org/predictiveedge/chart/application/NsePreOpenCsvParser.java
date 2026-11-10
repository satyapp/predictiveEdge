package org.predictiveedge.chart.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.predictiveedge.chart.domain.PreOpenSnapshot;

/** Strict parser for NSE's downloadable pre-open market CSV. */
public final class NsePreOpenCsvParser {
    private static final List<String> REQUIRED = List.of(
            "SYMBOL", "PREV. CLOSE", "BEST BID PRICE", "BEST ASK PRICE",
            "FINAL PRICE", "FINAL VOLUME", "VALUE");

    public ParseResult parse(String csv, Instant observedAt) {
        if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Pre-open CSV is empty");
        Objects.requireNonNull(observedAt, "Observation timestamp is required");
        List<List<String>> rows = rows(csv);
        if (rows.size() < 2) throw new IllegalArgumentException("Pre-open CSV contains no data rows");
        Map<String, Integer> columns = indexes(rows.getFirst());
        REQUIRED.forEach(name -> {
            if (!columns.containsKey(name)) throw new IllegalArgumentException("Pre-open CSV is missing " + name);
        });
        int width = rows.getFirst().size();
        var accepted = new ArrayList<PreOpenSnapshot>();
        var rejected = new ArrayList<RowRejection>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            String symbol = row.isEmpty() ? "ROW_" + (index + 1) : row.getFirst().trim();
            try {
                if (row.size() != width) throw new IllegalArgumentException("invalid column count");
                accepted.add(new PreOpenSnapshot("NSE", cell(row, columns, "SYMBOL"), "EQ",
                        decimal(row, columns, "PREV. CLOSE"), decimal(row, columns, "FINAL PRICE"),
                        whole(row, columns, "FINAL VOLUME"), decimal(row, columns, "VALUE"),
                        decimal(row, columns, "BEST BID PRICE"), decimal(row, columns, "BEST ASK PRICE"),
                        observedAt));
            } catch (RuntimeException invalid) {
                rejected.add(new RowRejection(index + 1, symbol, invalid.getMessage()));
            }
        }
        if (accepted.isEmpty()) throw new IllegalArgumentException("Pre-open CSV contains no usable final-price rows");
        return new ParseResult(accepted, rejected);
    }

    private static String cell(List<String> row, Map<String, Integer> columns, String name) {
        String value = row.get(columns.get(name)).trim();
        if (value.isEmpty() || "-".equals(value)) throw new IllegalArgumentException(name + " is unavailable");
        return value;
    }

    private static BigDecimal decimal(List<String> row, Map<String, Integer> columns, String name) {
        try {
            return new BigDecimal(cell(row, columns, name).replace(",", ""));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " is not numeric");
        }
    }

    private static long whole(List<String> row, Map<String, Integer> columns, String name) {
        try {
            return Long.parseLong(cell(row, columns, name).replace(",", ""));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(name + " is not a whole number");
        }
    }

    private static Map<String, Integer> indexes(List<String> header) {
        var result = new LinkedHashMap<String, Integer>();
        for (int index = 0; index < header.size(); index++) {
            result.put(header.get(index).replace("\uFEFF", "").trim().toUpperCase(Locale.ROOT), index);
        }
        return result;
    }

    private static List<List<String>> rows(String csv) {
        var result = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < csv.length(); index++) {
            char value = csv.charAt(index);
            if (quoted) {
                if (value == '"') {
                    if (index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                        field.append('"'); index++;
                    } else quoted = false;
                } else field.append(value);
            } else if (value == '"') {
                if (!field.isEmpty()) throw new IllegalArgumentException("Pre-open CSV contains a misplaced quote");
                quoted = true;
            } else if (value == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (value == '\r' || value == '\n') {
                if (value == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') index++;
                row.add(field.toString()); field.setLength(0);
                if (row.stream().anyMatch(cell -> !cell.isBlank())) result.add(List.copyOf(row));
                row.clear();
            } else field.append(value);
        }
        if (quoted) throw new IllegalArgumentException("Pre-open CSV contains an unterminated quote");
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            if (row.stream().anyMatch(cell -> !cell.isBlank())) result.add(List.copyOf(row));
        }
        return List.copyOf(result);
    }

    public record ParseResult(List<PreOpenSnapshot> snapshots, List<RowRejection> rejectedRows) {
        public ParseResult {
            snapshots = List.copyOf(snapshots);
            rejectedRows = List.copyOf(rejectedRows);
        }
    }

    public record RowRejection(int rowNumber, String symbol, String reason) { }
}
