package org.predictiveedge.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.application.SearchEvidenceCategory;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneActivityStore.EvidenceSave;
import org.predictiveedge.chart.application.StageOneActivityStore.IntradayListSave;
import org.predictiveedge.chart.application.StageOneActivityStore.ScreenerRow;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionStart;
import org.predictiveedge.chart.application.StageOneActivityStore.StockSelection;
import org.predictiveedge.chart.application.StageOneActivityStore.ScreenerSearch;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectedStockInput;
import org.predictiveedge.chart.application.StageOneActivityStore.SelectionBatch;
import org.predictiveedge.chart.application.StageOneActivityStore.StageOneAnalysisInput;
import org.predictiveedge.chart.domain.MarketScreenType;
import org.predictiveedge.chart.infrastructure.persistence.JpaStageOneActivityStore;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs the Stage-1 JPA mappings against PostgreSQL 16 with every migration applied. Hibernate runs in
 * {@code validate} mode, so a mapping that drifts from V018 fails here before it reaches the application.
 */
class StageOneActivityStoreIntegrationTest {
    private static final Instant SEARCHED_AT = Instant.parse("2026-11-25T03:45:00Z");
    private static final LocalDate TRADING_DATE = LocalDate.of(2026, 11, 25);

    private static PostgreSQLContainer<?> postgres;
    private static EntityManagerFactory entityManagers;
    private static JdbcTemplate jdbc;
    private static StageOneActivityStore store;

    @BeforeAll
    static void startDatabase() throws Exception {
        // Runs wherever Docker is available. Without Docker the test is reported as skipped.
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the Stage-1 persistence test");
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
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
        if (postgres != null) postgres.stop();
    }

    @Test
    void persistsTheWholeStageOneFlowAndSatisfiesEveryForeignKey() {
        UUID userId = UUID.randomUUID();
        UUID searchId = UUID.randomUUID();
        UUID infy = UUID.randomUUID();
        UUID tcs = UUID.randomUUID();
        UUID listId = UUID.randomUUID();

        store.recordSearch(search(userId, searchId, List.of(
                new ScreenerRow("infy", 1, "{\"symbol\":\"INFY\",\"percentChange\":3.2}"),
                new ScreenerRow("tcs", 2, "{\"symbol\":\"TCS\",\"percentChange\":2.1}"))));
        store.recordSelections(new SelectionBatch(searchId, userId, SearchEvidenceCategory.TOP_GAINERS,
                SEARCHED_AT.plusSeconds(60), List.of(
                        new SelectedStockInput(infy, "INFY", evidence("INFY"), analysis()),
                        new SelectedStockInput(tcs, "TCS", evidence("TCS"), analysis()))));
        store.recordIntradayList(new IntradayListSave(listId, userId, SEARCHED_AT.plusSeconds(120),
                List.of(infy, tcs)));

        assertThat(jdbc.queryForObject(
                "select result_count || ':' || stage || ':' || category from market_intelligence.search_result"
                        + " where search_id = ?", String.class, searchId))
                .isEqualTo("2:STAGE_1:TOP_GAINERS");
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.search_result_stock_top_gainers where search_id = ?",
                Integer.class, searchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select broker_row->>'symbol' from market_intelligence.search_result_stock_top_gainers"
                        + " where search_id = ? and screen_rank = 1", String.class, searchId)).isEqualTo("INFY");
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.selected_stock where search_id = ? and stage = 'STAGE_1'",
                Integer.class, searchId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.selected_stock_evidence_market_data where selection_id = ?",
                Integer.class, infy)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "select payload->>'lastPrice' from market_intelligence.selected_stock_evidence_market_data"
                        + " where selection_id = ? and source = 'NSE'", String.class, infy)).isEqualTo("1500.5");
        assertThat(jdbc.queryForObject(
                "select payload is null from market_intelligence.selected_stock_evidence_market_data"
                        + " where selection_id = ? and source = 'ZERODHA'", Boolean.class, infy)).isTrue();
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.selected_stock_evidence_news_and_sentiment"
                        + " where selection_id = ?", Integer.class, tcs)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select stance || ':' || model from market_intelligence.selected_stock_ai_analysis"
                        + " where selection_id = ? and stage = 'STAGE_1'", String.class, tcs))
                .isEqualTo("POSITIVE:qwen3:8b");
        assertThat(jdbc.queryForObject(
                "select item_count || ':' || stage from market_intelligence.intraday_list where list_id = ?",
                String.class, listId)).isEqualTo("2:STAGE_1");
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.intraday_list_item where list_id = ? and stage = 'STAGE_1'",
                Integer.class, listId)).isEqualTo(2);
    }

    @Test
    void rejectsASecondAnalysisOfTheSameStockInTheSameSearch() {
        UUID userId = UUID.randomUUID();
        UUID searchId = UUID.randomUUID();
        store.recordSearch(search(userId, searchId, List.of(new ScreenerRow("hdfcbank", 1, "{}"))));
        store.recordSelections(new SelectionBatch(searchId, userId, SearchEvidenceCategory.TOP_GAINERS,
                SEARCHED_AT, List.of(new SelectedStockInput(UUID.randomUUID(), "HDFCBANK", evidence("HDFCBANK"),
                        analysis()))));

        assertThatThrownBy(() -> store.recordSelections(new SelectionBatch(searchId, userId,
                SearchEvidenceCategory.TOP_GAINERS, SEARCHED_AT, List.of(new SelectedStockInput(
                        UUID.randomUUID(), "HDFCBANK", evidence("HDFCBANK"), analysis())))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already selected");
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.selected_stock where search_id = ?", Integer.class,
                searchId)).isEqualTo(1);
    }

    @Test
    void rejectsAnIntradayListThatContainsAnotherUsersSelection() {
        UUID owner = UUID.randomUUID();
        UUID otherUser = UUID.randomUUID();
        UUID searchId = UUID.randomUUID();
        UUID selection = UUID.randomUUID();
        UUID listId = UUID.randomUUID();
        store.recordSearch(search(owner, searchId, List.of(new ScreenerRow("wipro", 1, "{}"))));
        store.recordSelections(new SelectionBatch(searchId, owner, SearchEvidenceCategory.TOP_GAINERS, SEARCHED_AT,
                List.of(new SelectedStockInput(selection, "WIPRO", evidence("WIPRO"), analysis()))));

        assertThatThrownBy(() -> store.recordIntradayList(
                new IntradayListSave(listId, otherUser, SEARCHED_AT, List.of(selection))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another user");
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.intraday_list where list_id = ?", Integer.class, listId))
                .isZero();
    }

    @Test
    void rejectsASelectionWhoseScreenerRunWasNotSaved() {
        UUID userId = UUID.randomUUID();
        UUID missingSearch = UUID.randomUUID();

        assertThatThrownBy(() -> store.recordSelections(new SelectionBatch(missingSearch, userId,
                SearchEvidenceCategory.GAP_UP, SEARCHED_AT, List.of(new SelectedStockInput(UUID.randomUUID(),
                        "ITC", evidence("ITC"), analysis())))))
                .isInstanceOfAny(DataAccessException.class, PersistenceException.class);
    }

    @Test
    void recordsSelectedStocksBeforeEachDataTypeAndRejectsEvidenceWithoutASelection() {
        UUID userId = UUID.randomUUID();
        UUID searchId = UUID.randomUUID();
        UUID selection = UUID.randomUUID();
        store.recordSearch(search(userId, searchId, List.of(new ScreenerRow("sbin", 1, "{}"))));
        store.recordSelectedStocks(new SelectionStart(searchId, userId, SearchEvidenceCategory.TOP_GAINERS,
                SEARCHED_AT, List.of(new StockSelection(selection, "sbin"))));
        store.recordEvidence(new EvidenceSave(selection, "MARKET_DATA", """
                [{"domain": "MARKET_DATA", "source": "NSE", "role": "PRIMARY", "status": "SUCCESS",
                  "fetched_at": "2026-11-25T03:46:00Z", "payload": {"lastPrice": 812.4}, "detail": ""}]
                """));
        store.recordEvidence(new EvidenceSave(selection, "TECHNICAL_ANALYSIS", "[]"));

        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.selected_stock where selection_id = ? and stage = 'STAGE_1'",
                Integer.class, selection)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select payload->>'lastPrice' from market_intelligence.selected_stock_evidence_market_data"
                        + " where selection_id = ? and source = 'NSE'", String.class, selection)).isEqualTo("812.4");
        assertThat(jdbc.queryForObject(
                "select count(*) from market_intelligence.selected_stock_ai_analysis where selection_id = ?",
                Integer.class, selection)).isZero();
        assertThatThrownBy(() -> store.recordEvidence(new EvidenceSave(UUID.randomUUID(), "MARKET_DATA", "[]")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be recorded");
    }

    private static ScreenerSearch search(UUID userId, UUID searchId, List<ScreenerRow> rows) {
        return new ScreenerSearch(searchId, userId, SearchEvidenceCategory.TOP_GAINERS,
                MarketScreenType.TOP_GAINERS, "INTRADAY_SURGES", "NSE_EQ", "5m", TRADING_DATE, "account-1",
                "{\"historyDays\":365}", SEARCHED_AT, rows);
    }

    private static StageOneAnalysisInput analysis() {
        return new StageOneAnalysisInput("POSITIVE", "Trend is up", "Opening range holds", "Gap reversal",
                "qwen3:8b", SEARCHED_AT.plusSeconds(90), "{\"stance\":\"POSITIVE\"}");
    }

    /** Evidence in the shape {@code StageOneIntelligenceEvidenceCollector} writes per stock. */
    private static String evidence(String symbol) {
        return """
                {
                  "MARKET_DATA": [
                    {"domain": "MARKET_DATA", "source": "NSE", "role": "PRIMARY", "status": "SUCCESS",
                     "fetched_at": "2026-11-25T03:46:00Z", "payload": {"symbol": "%1$s", "lastPrice": 1500.5},
                     "detail": ""},
                    {"domain": "MARKET_DATA", "source": "ZERODHA", "role": "SUPPLEMENTARY", "status": "UNAVAILABLE",
                     "fetched_at": "2026-11-25T03:46:00Z", "payload": null, "detail": "Broker context missing"}
                  ],
                  "NEWS_AND_SENTIMENT": [
                    {"domain": "NEWS_AND_SENTIMENT", "source": "TRENDLYNE", "role": "SUPPLEMENTARY",
                     "status": "SUCCESS", "fetched_at": "2026-11-25T03:46:00Z", "payload": {"headlines": 3},
                     "detail": ""}
                  ],
                  "MARKET_DEPTH": []
                }
                """.formatted(symbol);
    }
}
