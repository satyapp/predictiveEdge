package org.predictiveedge.chart.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.predictiveedge.chart.domain.MarketScreenCandidateRanker;
import org.predictiveedge.chart.domain.MarketScreenType;

/** Normalizes and ranks a single market-screen CSV before historical/confluence enrichment. */
public final class MarketScreenCsvAnalysisService {
    private final MarketScreenCsvParserRegistry parsers;
    private final MarketScreenCandidateRanker ranker;
    private final Clock clock;

    public MarketScreenCsvAnalysisService(MarketScreenCsvParserRegistry parsers,
            MarketScreenCandidateRanker ranker, Clock clock) {
        this.parsers = Objects.requireNonNull(parsers);
        this.ranker = Objects.requireNonNull(ranker);
        this.clock = Objects.requireNonNull(clock);
    }

    public Result analyze(Command command) {
        Objects.requireNonNull(command, "Market-screen command is required");
        var parsed = parsers.parse(command.screenType(), command.csv(), command.observedAt());
        return new Result("IDEA_CANDIDATE_ONLY", command.screenType(), clock.instant(),
                parsed.observations().size(), parsed.rejectedRows(), ranker.rank(parsed.observations()));
    }

    public List<MarketScreenType> supportedTypes() {
        return parsers.supportedTypes().stream().sorted().toList();
    }

    public record Command(MarketScreenType screenType, String csv, Instant observedAt) {
        public Command {
            Objects.requireNonNull(screenType, "Screen type is required");
            if (csv == null || csv.isBlank()) throw new IllegalArgumentException("Market-screen CSV is empty");
            Objects.requireNonNull(observedAt, "Observation timestamp is required");
        }
    }

    public record Result(String authority, MarketScreenType screenType, Instant analyzedAt, int parsedRows,
            List<MarketScreenCsvParser.RowRejection> rejectedRows,
            MarketScreenCandidateRanker.RankedScreen ranking) { }
}
