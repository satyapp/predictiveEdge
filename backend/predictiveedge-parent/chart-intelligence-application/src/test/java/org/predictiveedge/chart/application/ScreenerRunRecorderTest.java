package org.predictiveedge.chart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.StageOneActivityStore.IntradayListSave;
import org.predictiveedge.chart.application.StageOneActivityStore.ScreenerSearch;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionBatch;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionStart;
import org.predictiveedge.chart.application.StageOneActivityStore.EvidenceSave;
import org.predictiveedge.chart.application.StageOneActivityStore.StageOneAnalysisInput;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.MarketScreenType;
import org.predictiveedge.chart.domain.PreOpenSnapshot;

class ScreenerRunRecorderTest {
    private static final Instant FETCHED_AT = Instant.parse("2026-11-25T04:10:00Z");
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 11, 25);
    private static final UUID USER = UUID.randomUUID();

    @Test
    void savesTheRunWithOneRowPerSymbolKeepingTheBetterRank() {
        var store = new CapturingStore();
        var copy = copy(MarketScreenType.TOP_GAINERS,
                row(MarketScreenType.TOP_GAINERS, "TCS", 2, "3.10"),
                row(MarketScreenType.TOP_GAINERS, "INFY", 1, "4.20"),
                row(MarketScreenType.TOP_GAINERS, "TCS", 3, "2.00"));

        var run = new ScreenerRunRecorder(store).record(USER, "broker-1", TRADING_DATE, copy, "breakout", "NIFTY",
                "5m", "{\"screenType\":\"TOP_GAINERS\"}", observation -> "{\"symbol\":\"" + observation.symbol() + "\"}");

        ScreenerSearch saved = store.searches.get(0);
        assertThat(saved.searchId()).isEqualTo(run.searchId());
        assertThat(saved.category()).isEqualTo(SearchEvidenceCategory.TOP_GAINERS);
        assertThat(saved.screenType()).isEqualTo("TOP_GAINERS");
        assertThat(saved.setup()).isEqualTo("breakout");
        assertThat(saved.universe()).isEqualTo("NIFTY");
        assertThat(saved.timeframe()).isEqualTo("5m");
        assertThat(saved.tradingDate()).isEqualTo(TRADING_DATE);
        assertThat(saved.brokerAccountId()).isEqualTo("broker-1");
        assertThat(saved.criteriaJson()).isEqualTo("{\"screenType\":\"TOP_GAINERS\"}");
        assertThat(saved.searchedAt()).isEqualTo(FETCHED_AT);
        assertThat(saved.rows()).extracting(StageOneActivityStore.ScreenerRow::symbol, StageOneActivityStore.ScreenerRow::screenRank)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("INFY", 1), org.assertj.core.groups.Tuple.tuple("TCS", 2));
        assertThat(run.category()).isEqualTo(SearchEvidenceCategory.TOP_GAINERS);
    }

    @Test
    void eachRunGetsANewSearchId() {
        var store = new CapturingStore();
        var recorder = new ScreenerRunRecorder(store);
        var copy = copy(MarketScreenType.TOP_LOSERS, row(MarketScreenType.TOP_LOSERS, "ZEE", 1, "-5.00"));

        var first = recorder.record(USER, "broker-1", TRADING_DATE, copy, "", "", "", "{}", o -> "{}");
        var second = recorder.record(USER, "broker-1", TRADING_DATE, copy, "", "", "", "{}", o -> "{}");

        assertThat(first.searchId()).isNotEqualTo(second.searchId());
        assertThat(first.category()).isEqualTo(SearchEvidenceCategory.TOP_LOSERS);
    }

    @Test
    void mapsEachScreenTypeToItsCategory() {
        assertThat(SearchEvidenceCategory.forScreen(MarketScreenType.NEW_52_WEEK_HIGH, rows(MarketScreenType.NEW_52_WEEK_HIGH, "1.00")))
                .isEqualTo(SearchEvidenceCategory.WEEK_52_HIGH);
        assertThat(SearchEvidenceCategory.forScreen(MarketScreenType.NEW_52_WEEK_LOW, rows(MarketScreenType.NEW_52_WEEK_LOW, "1.00")))
                .isEqualTo(SearchEvidenceCategory.WEEK_52_LOW);
        assertThat(SearchEvidenceCategory.forScreen(MarketScreenType.MOST_ACTIVE_EQUITIES, rows(MarketScreenType.MOST_ACTIVE_EQUITIES, "1.00")))
                .isEqualTo(SearchEvidenceCategory.MOST_ACTIVE_BY_VOLUME);
        assertThat(SearchEvidenceCategory.forScreen(MarketScreenType.VOLUME_GAINERS, rows(MarketScreenType.VOLUME_GAINERS, "1.00")))
                .isEqualTo(SearchEvidenceCategory.VOLUME_GAINERS);
    }

    @Test
    void directionalScreensTakeTheirCategoryFromTheRankOneRow() {
        var falling = List.of(
                observation(MarketScreenType.PRICE_BAND_HITTERS, "AAA", 2, "1.00"),
                observation(MarketScreenType.PRICE_BAND_HITTERS, "BBB", 1, "-2.50"));
        var rising = List.of(
                observation(MarketScreenType.INTRADAY_OPPORTUNITY, "CCC", 1, "0.00"));

        assertThat(SearchEvidenceCategory.forScreen(MarketScreenType.PRICE_BAND_HITTERS, falling))
                .isEqualTo(SearchEvidenceCategory.LOWER_CIRCUIT);
        assertThat(SearchEvidenceCategory.forScreen(MarketScreenType.INTRADAY_OPPORTUNITY, rising))
                .isEqualTo(SearchEvidenceCategory.INTRADAY_SURGE);
    }

    @Test
    void savesThePreOpenShortlistAsARunInTheOrderItWasRanked() {
        var store = new CapturingStore();
        var first = preOpen("SBIN", "150.00");
        var second = preOpen("TCS", "3900.00");

        var run = new ScreenerRunRecorder(store).recordPreOpen(USER, "broker-1", TRADING_DATE,
                List.of(first, second), "{\"historyDays\":365}", snapshot -> "{\"symbol\":\"" + snapshot.symbol() + "\"}",
                FETCHED_AT);

        ScreenerSearch saved = store.searches.get(0);
        assertThat(run.category()).isEqualTo(SearchEvidenceCategory.PRE_OPEN);
        assertThat(saved.category()).isEqualTo(SearchEvidenceCategory.PRE_OPEN);
        assertThat(saved.screenType()).isEqualTo("PRE_OPEN");
        assertThat(saved.searchedAt()).isEqualTo(FETCHED_AT);
        assertThat(saved.rows()).extracting(StageOneActivityStore.ScreenerRow::symbol, StageOneActivityStore.ScreenerRow::screenRank)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SBIN", 1), org.assertj.core.groups.Tuple.tuple("TCS", 2));
    }

    private static PreOpenSnapshot preOpen(String symbol, String price) {
        var value = new BigDecimal(price);
        return new PreOpenSnapshot("NSE", symbol, "EQ", value, value.add(BigDecimal.ONE), 1_000L,
                value.multiply(BigDecimal.valueOf(1000)), value, value.add(BigDecimal.ONE), FETCHED_AT);
    }

    @Test
    void rejectsAMissingCopyBeforeWriting() {
        var store = new CapturingStore();
        assertThatThrownBy(() -> new ScreenerRunRecorder(store).record(USER, "broker-1", TRADING_DATE, null, "", "",
                "", "{}", o -> "{}"))
                .isInstanceOf(NullPointerException.class);
        assertThat(store.searches).isEmpty();
    }

    private static MarketScreenDataCopy copy(MarketScreenType type, MarketScreenObservation... rows) {
        return new MarketScreenDataCopy(type, "NSE_MCP", FETCHED_AT, FETCHED_AT, "LIVE", List.of(rows), List.of());
    }

    private static List<MarketScreenObservation> rows(MarketScreenType type, String percentChange) {
        return List.of(observation(type, "ABC", 1, percentChange));
    }

    private static MarketScreenObservation row(MarketScreenType type, String symbol, int rank, String percentChange) {
        return observation(type, symbol, rank, percentChange);
    }

    private static MarketScreenObservation observation(MarketScreenType type, String symbol, int rank, String percentChange) {
        var price = new BigDecimal("100.00");
        return new MarketScreenObservation(type, "NSE", symbol, "EQ", rank, price, price.add(BigDecimal.ONE),
                price.subtract(BigDecimal.ONE), price, price, new BigDecimal(percentChange), 1_000L,
                new BigDecimal("100000"), "", FETCHED_AT);
    }

    /** Captures only what the recorder writes; every other store call is unused here. */
    private static final class CapturingStore implements StageOneActivityStore {
        private final List<ScreenerSearch> searches = new ArrayList<>();

        @Override
        public void recordSearch(ScreenerSearch search) {
            searches.add(search);
        }

        @Override
        public void recordSelections(SelectionBatch batch) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordIntradayList(IntradayListSave list) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordSelectedStocks(SelectionStart start) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordEvidence(EvidenceSave evidence) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordAnalysis(UUID selectionId, StageOneAnalysisInput analysis) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String evidenceByDomainJson(UUID selectionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, UUID> selectionIdsBySymbol(UUID userId, UUID searchId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<StageOneActivityStore.PreOpenRow> preOpenRowForSearch(UUID userId, UUID searchId,
                String symbol) {
            return java.util.Optional.empty();
        }
    }
}
