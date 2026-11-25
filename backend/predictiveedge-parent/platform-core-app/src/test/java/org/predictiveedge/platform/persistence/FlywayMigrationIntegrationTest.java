package org.predictiveedge.platform.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Applies every migration to an empty PostgreSQL 16 database, so a broken migration fails the build. */
class FlywayMigrationIntegrationTest {
    private static final int EXPECTED_MIGRATIONS = 18;
    private static final int MARKET_INTELLIGENCE_TABLES = 42;

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void startDatabase() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) postgres.stop();
    }

    @Test
    void appliesEveryMigrationToAnEmptyDatabase() throws Exception {
        var flyway = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();

        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(EXPECTED_MIGRATIONS);
        assertThat(countTables("market_intelligence")).isEqualTo(MARKET_INTELLIGENCE_TABLES);
    }

    private static int countTables(String schema) throws Exception {
        try (var connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "select count(*) from information_schema.tables where table_schema = '" + schema + "'")) {
            result.next();
            return result.getInt(1);
        }
    }
}
