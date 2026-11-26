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
import org.predictiveedge.chart.application.MarketScreenDataCopy;
import org.predictiveedge.chart.application.ScreenerRunRecorder;
import org.predictiveedge.chart.application.StageOneActivityStore;
import org.predictiveedge.chart.application.StageOneActivityStore.StageOneAnalysisInput;
import org.predictiveedge.chart.domain.MarketScreenObservation;
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
