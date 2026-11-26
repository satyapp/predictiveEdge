package org.predictiveedge.chart.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.predictiveedge.broker.spi.BrokerContext;
import org.predictiveedge.chart.application.IntradayListRecorder;
import org.predictiveedge.chart.application.MarketScreenDataCopy;
import org.predictiveedge.chart.application.PreOpenOllamaAnalysis;
import org.predictiveedge.chart.application.ScreenerRunRecorder;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneActivityStore.StageOneAnalysisInput;
import org.predictiveedge.chart.domain.MarketScreenObservation;
import org.predictiveedge.chart.domain.PreOpenAuctionDetail;
import org.predictiveedge.chart.domain.PreOpenSnapshot;
import org.predictiveedge.chart.domain.MarketScreenType;
import org.predictiveedge.chart.infrastructure.persistence.JpaStageOneActivityStore;
import org.predictiveedge.decision.domain.DecisionResourceType;
import org.predictiveedge.intelligence.connector.EvidenceDomain;
import org.predictiveedge.intelligence.connector.EvidenceFetchStatus;
import org.predictiveedge.intelligence.connector.EvidenceQuery;
import org.predictiveedge.intelligence.connector.EvidenceSource;
import org.predictiveedge.intelligence.connector.IntelligenceDataConnector;
import org.predictiveedge.intelligence.connector.ProviderEvidence;
import org.predictiveedge.intelligence.connector.ResourceEvidenceBundle;
import org.predictiveedge.intelligence.connector.SourceRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs one Stage-1 screener run through the real recorder, parallel collector, JPA store and PostgreSQL. The database
 * comes from {@code PREDICTIVEEDGE_TEST_JDBC_URL}, {@code PREDICTIVEEDGE_TEST_JDBC_USER} and
 * {@code PREDICTIVEEDGE_TEST_JDBC_PASSWORD} when set, so it runs on a local server without Docker. Otherwise it starts
 * a PostgreSQL container and is skipped when Docker is unavailable. Hibernate validates every mapping against V001 to
 * V018.
 */
class StageOneRunPersistenceIntegrationTest {
    private static final Instant CUTOFF = Instant.parse("2026-11-25T04:10:00Z");
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 11, 25);

    private static PostgreSQLContainer<?> container;
    private static EntityManagerFactory entityManagers;
    private static JdbcTemplate jdbc;
    private static StageOneActivityStore store;

    @BeforeAll
    static void startDatabase() {
        String url = System.getenv("PREDICTIVEEDGE_TEST_JDBC_URL");
        String user;
        String password;
        if (url != null && !url.isBlank()) {
            user = System.getenv("PREDICTIVEEDGE_TEST_JDBC_USER");
            password = System.getenv("PREDICTIVEEDGE_TEST_JDBC_PASSWORD");
        } else {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                    "Set PREDICTIVEEDGE_TEST_JDBC_URL or start Docker to run the Stage-1 persistence test");
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();

        var dataSource = new DriverManagerDataSource(url, user, password);
        jdbc = new JdbcTemplate(dataSource);
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("org.predictiveedge.chart.infrastructure.persistence");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate"));
        factory.afterPropertiesSet();
        entityManagers = factory.getObject();
        store = new JpaStageOneActivityStore(entityManagers, new JpaTransactionManager(entityManagers),
                new ObjectMapper());
    }

    @AfterAll
    static void stopDatabase() {
        if (entityManagers != null) entityManagers.close();
        if (container != null) container.stop();
    }

    @Test
    void savesSearchSelectionsEvidenceAndAnalysisAndCollatesFromTheStoredRows() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        UUID userId = UUID.randomUUID();
        var copy = new MarketScreenDataCopy(MarketScreenType.TOP_GAINERS, "NSE_MCP", CUTOFF, CUTOFF, "LIVE",
                List.of(observation("INFY", 1, "3.20"), observation("TCS", 2, "2.10")), List.of());

        var run = new ScreenerRunRecorder(store).record(userId, "broker-it", TRADING_DATE, copy, "breakout",
                "NIFTY", "5m", "{\"screenType\":\"TOP_GAINERS\"}", row -> write(json, row));

        assertThat(count("select count(*) from market_intelligence.search_result where search_id = ?", run.searchId()))
                .isEqualTo(1);
        assertThat(count("select count(*) from market_intelligence.search_result_stock_top_gainers where search_id = ?",
                run.searchId())).isEqualTo(2);

        var stage = new StageOneStoreEvidenceWriter(store, json, run.searchId(), userId, run.category(), CUTOFF);
        var envelope = new StageOneIntelligenceEvidenceCollector(new FixedConnector(), json, 2)
                .collect(List.of("INFY", "TCS"), CUTOFF, BrokerContext.withoutCredentials(userId, "broker-it"), stage);

        assertThat(count("select count(*) from market_intelligence.selected_stock where search_id = ?", run.searchId()))
                .isEqualTo(2);
        assertThat(count("select count(*) from market_intelligence.selected_stock_evidence_technical_analysis e "
                + "join market_intelligence.selected_stock s on s.selection_id = e.selection_id where s.search_id = ?",
                run.searchId())).isEqualTo(2);
        assertThat(count("select count(*) from market_intelligence.selected_stock_evidence_market_depth e "
                + "join market_intelligence.selected_stock s on s.selection_id = e.selection_id "
                + "where s.search_id = ? and e.status = 'UNAVAILABLE'", run.searchId())).isEqualTo(2);

        assertThat(envelope.at("/stocks")).hasSize(2);
        assertThat(envelope.at("/stocks/0/symbol").asText()).isEqualTo("INFY");
        assertThat(envelope.at("/stocks/0/evidence_by_domain/TECHNICAL_ANALYSIS/0/source").asText())
                .isEqualTo("TRENDLYNE");
        assertThat(envelope.at("/stocks/0/evidence_by_domain/TECHNICAL_ANALYSIS/0/payload/kind").asText())
                .isEqualTo("TECHNICAL_ANALYSIS");
        assertThat(envelope.at("/stocks/1/evidence_by_domain/MARKET_DEPTH/0/status").asText())
                .isEqualTo("UNAVAILABLE");
        assertThat(envelope.at("/stocks/0/intelligence_modules")).hasSize(DecisionResourceType.values().length);

        var selections = stage.selectionIds();
        UUID infy = selections.get("INFY");
        var storedByDomain = json.readTree(store.evidenceByDomainJson(infy));
        assertThat(storedByDomain.size()).isEqualTo(EvidenceDomain.values().length);
        assertThat(storedByDomain.at("/TECHNICAL_ANALYSIS/0/source").asText()).isEqualTo("TRENDLYNE");

        store.recordAnalysis(infy, new StageOneAnalysisInput("LONG_WATCH", "Trend intact above the 20 EMA",
                "Hold above the opening range", "Stop below the swing low", "qwen3:8b", CUTOFF,
                "{\"symbol\":\"INFY\"}"));
        assertThat(count("select count(*) from market_intelligence.selected_stock_ai_analysis where selection_id = ? "
                + "and stance = 'LONG_WATCH'", infy)).isEqualTo(1);
        assertThatThrownBy(() -> store.recordAnalysis(infy, new StageOneAnalysisInput("WAIT", "Second view",
                "Confirm", "Risk", "qwen3:8b", CUTOFF, "{\"symbol\":\"INFY\"}")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has Stage-1 analysis");
    }

    @Test
    void savesAnIntradayListOfAnalysedSelectionsAndRejectsOthers() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        UUID userId = UUID.randomUUID();
        var copy = new MarketScreenDataCopy(MarketScreenType.TOP_GAINERS, "NSE_MCP", CUTOFF, CUTOFF, "LIVE",
                List.of(observation("INFY", 1, "3.20"), observation("TCS", 2, "2.10")), List.of());
        var run = new ScreenerRunRecorder(store).record(userId, "broker-it", TRADING_DATE, copy, "breakout",
                "NIFTY", "5m", "{}", row -> write(json, row));
        var stage = new StageOneStoreEvidenceWriter(store, json, run.searchId(), userId, run.category(), CUTOFF);
        new StageOneIntelligenceEvidenceCollector(new FixedConnector(), json, 2)
                .collect(List.of("INFY", "TCS"), CUTOFF, null, stage);
        UUID infy = stage.selectionIds().get("INFY");
        store.recordAnalysis(infy, new StageOneAnalysisInput("LONG_WATCH", "Trend intact", "Hold the range",
                "Stop below the low", "qwen3:8b", CUTOFF, "{\"symbol\":\"INFY\"}"));
        var lists = new IntradayListRecorder(store);

        UUID listId = lists.record(userId, run.searchId(), List.of("infy"), CUTOFF);

        assertThat(count("select count(*) from market_intelligence.intraday_list where list_id = ? and item_count = 1 "
                + "and stage = 'STAGE_1'", listId)).isEqualTo(1);
        assertThat(count("select count(*) from market_intelligence.intraday_list_item where list_id = ? "
                + "and symbol = 'INFY' and selection_id = ?", listId, infy)).isEqualTo(1);
        assertThatThrownBy(() -> lists.record(userId, run.searchId(), List.of("TCS"), CUTOFF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TCS has no Stage-1 analysis");
        assertThatThrownBy(() -> lists.record(userId, run.searchId(), List.of("WIPRO"), CUTOFF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("was not selected from this search");
    }

    @Test
    void savesThePreOpenShortlistAndCollatesOnlyItsPreOpenData() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        UUID userId = UUID.randomUUID();
        var shortlist = List.of(preOpen("SBIN", "800.00"), preOpen("TCS", "3900.00"));
        var run = new ScreenerRunRecorder(store).recordPreOpen(userId, "broker-it", TRADING_DATE, shortlist,
                "{\"historyDays\":365}", snapshot -> write(json, PreOpenEvidenceConnector.payload(snapshot)), CUTOFF);

        assertThat(count("select count(*) from market_intelligence.search_result where search_id = ? "
                + "and category = 'PRE_OPEN' and screen_type = 'PRE_OPEN'", run.searchId())).isEqualTo(1);
        assertThat(count("select count(*) from market_intelligence.search_result_pre_open where search_id = ?",
                run.searchId())).isEqualTo(2);
        assertThat(count("select count(*) from market_intelligence.search_result_pre_open where search_id = ? "
                + "and prev_close is not null and best_bid_qty = 500 and best_ask_qty = 700 "
                + "and indicative_equilibrium_qty = 900 and final_volume = 1000 and week_52_low is not null",
                run.searchId())).isEqualTo(2);

        var connector = new PreOpenEvidenceConnector(Map.of("SBIN", shortlist.get(0), "TCS", shortlist.get(1)));
        var stage = new StageOneStoreEvidenceWriter(store, json, run.searchId(), userId, run.category(), CUTOFF);
        var envelope = new StageOneIntelligenceEvidenceCollector(connector, json, 2)
                .collect(List.of("SBIN", "TCS"), CUTOFF, null, stage);

        assertThat(count("select count(*) from market_intelligence.stock_evidence_pre_open e "
                + "join market_intelligence.selected_stock s on s.selection_id = e.selection_id "
                + "where s.search_id = ? and e.source = 'NSE' and e.status = 'SUCCESS'", run.searchId()))
                .isEqualTo(2);
        assertThat(count("select count(*) from market_intelligence.selected_stock_evidence_market_data e "
                + "join market_intelligence.selected_stock s on s.selection_id = e.selection_id "
                + "where s.search_id = ?", run.searchId())).isZero();
        assertThat(envelope.at("/stocks/0/evidence_by_domain/PRE_OPEN_ANALYSIS/0/payload/kind").asText())
                .isEqualTo("PRE_OPEN_AUCTION_SNAPSHOT");
        assertThat(envelope.at("/stocks/1/evidence_by_domain/MARKET_DATA")).isEmpty();

        UUID sbin = stage.selectionIds().get("SBIN");
        UUID tcs = stage.selectionIds().get("TCS");
        stage.recordAnalyses(new PreOpenOllamaAnalysis("qwen3:8b", CUTOFF, true, "MIXED", "Two stocks",
                List.of(new PreOpenOllamaAnalysis.CandidateInterpretation("SBIN", "LONG_WATCH", "Gap held",
                                "Hold the auction price", "Stop below the open"),
                        new PreOpenOllamaAnalysis.CandidateInterpretation("TCS", "WAIT", "Gap not confirmed",
                                "Wait for volume", "Gap may fill")),
                List.of()));
        assertThat(count("select count(*) from market_intelligence.selected_stock_ai_analysis where selection_id in (?, ?)",
                sbin, tcs)).isEqualTo(2);
    }

    @Test
    void givesEachSelectedTopGainerItsPreOpenRowOfTheTradingDay() throws Exception {
        var json = new ObjectMapper().findAndRegisterModules();
        UUID userId = UUID.randomUUID();
        // The pre-open run holds the whole day, including INFY and TCS, which the shortlist may not contain.
        var dayRows = List.of(preOpen("SBIN", "800.00"), preOpen("INFY", "1500.00"), preOpen("TCS", "3900.00"));
        new ScreenerRunRecorder(store).recordPreOpen(userId, "broker-it", TRADING_DATE, dayRows,
                "{\"historyDays\":365}", snapshot -> write(json, Map.of("symbol", snapshot.symbol())), CUTOFF);

        var copy = new MarketScreenDataCopy(MarketScreenType.TOP_GAINERS, "NSE_MCP", CUTOFF, CUTOFF, "LIVE",
                List.of(observation("INFY", 1, "3.20"), observation("TCS", 2, "2.10")), List.of());
        var run = new ScreenerRunRecorder(store).record(userId, "broker-it", TRADING_DATE, copy, "breakout",
                "NIFTY", "5m", "{\"screenType\":\"TOP_GAINERS\"}", row -> write(json, row));

        var stage = new StageOneStoreEvidenceWriter(store, json, run.searchId(), userId, run.category(), CUTOFF);
        var envelope = new StageOneIntelligenceEvidenceCollector(new FixedConnector(), json, 2)
                .withPreOpenRows(store, userId, run.searchId())
                .collect(List.of("INFY", "TCS"), CUTOFF, BrokerContext.withoutCredentials(userId, "broker-it"), stage);

        assertThat(envelope.at("/stocks/0/symbol").asText()).isEqualTo("INFY");
        assertThat(envelope.at("/stocks/0/evidence_by_domain/PRE_OPEN_ANALYSIS/0/payload/kind").asText())
                .isEqualTo("PRE_OPEN_SEARCH_RESULT");
        assertThat(envelope.at("/stocks/0/evidence_by_domain/PRE_OPEN_ANALYSIS/0/payload/screen_rank").asInt())
                .isEqualTo(2);
        assertThat(envelope.at("/stocks/0/evidence_by_domain/PRE_OPEN_ANALYSIS/0/payload/final_price").asDouble())
                .isEqualTo(1501.0);
        assertThat(envelope.at("/stocks/1/evidence_by_domain/PRE_OPEN_ANALYSIS/0/payload/symbol").asText())
                .isEqualTo("TCS");
        assertThat(count("select count(*) from market_intelligence.stock_evidence_pre_open e "
                + "join market_intelligence.selected_stock s on s.selection_id = e.selection_id "
                + "where s.search_id = ? and e.source = 'NSE' and e.status = 'SUCCESS'", run.searchId()))
                .isEqualTo(2);
    }

    private static PreOpenSnapshot preOpen(String symbol, String price) {
        BigDecimal value = new BigDecimal(price);
        var detail = new PreOpenAuctionDetail(value, value, 500L, value.add(BigDecimal.ONE), 700L,
                value, 900L, BigDecimal.ONE, BigDecimal.TEN, value.add(BigDecimal.ONE), 1_000L,
                value.multiply(BigDecimal.valueOf(1000)), BigDecimal.TEN, 10L, 20L,
                value.add(BigDecimal.TEN), value.subtract(BigDecimal.TEN));
        return new PreOpenSnapshot("NSE", symbol, "EQ", value, value.add(BigDecimal.ONE), 1_000L,
                value.multiply(BigDecimal.valueOf(1000)), value, value.add(BigDecimal.ONE), CUTOFF, detail);
    }

    private static String write(ObjectMapper json, Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static MarketScreenObservation observation(String symbol, int rank, String percentChange) {
        BigDecimal price = new BigDecimal("1500.00");
        return new MarketScreenObservation(MarketScreenType.TOP_GAINERS, "NSE", symbol, "EQ", rank, price,
                price.add(BigDecimal.TEN), price.subtract(BigDecimal.TEN), price, price,
                new BigDecimal(percentChange), 250_000L, new BigDecimal("37500000"), "", CUTOFF);
    }

    private static long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** Returns one successful row per domain, except market depth, which is reported unavailable. */
    private static final class FixedConnector implements IntelligenceDataConnector {
        @Override
        public List<ProviderEvidence> fetch(EvidenceDomain domain, EvidenceQuery query) {
            if (domain == EvidenceDomain.MARKET_DEPTH) {
                return List.of(new ProviderEvidence(domain, EvidenceSource.TRENDLYNE, SourceRole.SUPPLEMENTARY,
                        EvidenceFetchStatus.UNAVAILABLE, query.cutoff(), null, "No depth feed in this test"));
            }
            return List.of(new ProviderEvidence(domain, EvidenceSource.TRENDLYNE, SourceRole.SUPPLEMENTARY,
                    EvidenceFetchStatus.SUCCESS, query.cutoff(), Map.of("kind", domain.name(), "symbol",
                            query.symbol()), ""));
        }

        @Override
        public ResourceEvidenceBundle fetchForResource(DecisionResourceType resourceType, EvidenceQuery query) {
            throw new UnsupportedOperationException();
        }
    }
}
