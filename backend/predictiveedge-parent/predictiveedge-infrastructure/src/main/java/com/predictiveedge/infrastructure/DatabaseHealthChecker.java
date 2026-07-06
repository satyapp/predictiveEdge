package com.predictiveedge.infrastructure;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

@Component
public class DatabaseHealthChecker {

    private final DataSource dataSource;

    public DatabaseHealthChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isHealthy() {
        try (Connection c = dataSource.getConnection()) {
            return c != null && !c.isClosed();
        } catch (Exception e) {
            return false;
        }
    }
}
