package org.predictiveedge.chart.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionObservation;
import org.predictiveedge.marketintelligence.application.PreOpenAuctionPublicationPort;

/**
 * Reads an NSE pre-open CSV and keeps its valid rows. It stores each valid row as market data and returns the rows
 * so the caller can save them as a pre-open run. It does not shortlist stocks, check history or rank them.
 */
public final class PreOpenCsvAnalysisService {
    private final NsePreOpenCsvParser parser;
    private final PreOpenAuctionPublicationPort publications;
    private final Clock clock;

    public PreOpenCsvAnalysisService(
            NsePreOpenCsvParser parser, PreOpenAuctionPublicationPort publications, Clock clock) {
        this.parser = Objects.requireNonNull(parser, "CSV parser is required");
        this.publications = Objects.requireNonNull(publications, "Snapshot publication port is required");
        this.clock = Objects.requireNonNull(clock, "Clock is required");
    }

    public Result ingest(Command command) {
        Objects.requireNonNull(command, "CSV command is required");
        Instant startedAt = clock.instant();
        if (startedAt.isBefore(command.observedAt()))
            throw new IllegalArgumentException("Observation timestamp cannot be in the future");
        var parsed = parser.parse(command.csv(), command.observedAt());
        if (parsed.snapshots().isEmpty())
            throw new IllegalArgumentException("Pre-open CSV has no valid rows to save");
        parsed.snapshots().forEach(snapshot -> publications.publish(command.userId(), command.brokerAccountId(),
                observation(snapshot, command.tradingDate()), startedAt));
        // Most-traded first, so the stored order is the same on every run. This is sorting, not a score.
        List<PreOpenSnapshot> validRows = parsed.snapshots().stream()
                .sorted(Comparator.comparing(PreOpenSnapshot::auctionTurnover).reversed()
                        .thenComparing(PreOpenSnapshot::symbol))
                .toList();
        return new Result(parsed.snapshots().size(), parsed.rejectedRows(), validRows, startedAt);
    }

    private static PreOpenAuctionObservation observation(PreOpenSnapshot value, LocalDate tradingDate) {
        return new PreOpenAuctionObservation(value.venue(), value.symbol(), value.series(), tradingDate,
                value.previousClose(), value.auctionPrice(), value.auctionVolume(), value.auctionTurnover(),
                value.bestBid(), value.bestAsk(), value.observedAt());
    }

    public record Command(UUID userId, String brokerAccountId, String csv, LocalDate tradingDate, Instant observedAt) {
        public Command {
            Objects.requireNonNull(userId, "User id is required");
            if (brokerAccountId == null || brokerAccountId.isBlank())
                throw new IllegalArgumentException("Broker account id is required");
            brokerAccountId = brokerAccountId.trim();
            if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Pre-open CSV is empty");
            Objects.requireNonNull(tradingDate, "Trading date is required");
            Objects.requireNonNull(observedAt, "Observation timestamp is required");
        }
    }

    /** The counts of the parsed file, the rejected rows, and the valid rows to save in the order they are stored. */
    public record Result(
            int parsedRows,
            List<NsePreOpenCsvParser.RowRejection> rejectedRows,
            List<PreOpenSnapshot> validRows,
            Instant startedAt) { }
}
