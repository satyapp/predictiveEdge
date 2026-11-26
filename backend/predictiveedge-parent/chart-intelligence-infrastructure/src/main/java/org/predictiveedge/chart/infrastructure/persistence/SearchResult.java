package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One screener run: the criteria, the category and the number of rows it returned. */
@Entity
@Table(name = "search_result", schema = "market_intelligence")
public class SearchResult {
    @Id
    @Column(name = "search_id", nullable = false)
    private UUID searchId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "screen_type", nullable = false)
    private String screenType;

    @Column(name = "setup", nullable = false)
    private String setup;

    @Column(name = "universe", nullable = false)
    private String universe;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "trading_date", nullable = false)
    private LocalDate tradingDate;

    @Column(name = "broker_account_id", nullable = false)
    private String brokerAccountId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria_json", nullable = false)
    private String criteriaJson;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "stage", nullable = false)
    private String stage;

    @Column(name = "searched_at", nullable = false)
    private Instant searchedAt;

    protected SearchResult() { }

    public UUID searchId() { return searchId; }
    public UUID userId() { return userId; }
    public LocalDate tradingDate() { return tradingDate; }

    public SearchResult(UUID searchId, UUID userId, String category, String screenType, String setup,
            String universe, String timeframe, LocalDate tradingDate, String brokerAccountId, String criteriaJson,
            int resultCount, String stage, Instant searchedAt) {
        this.searchId = searchId;
        this.userId = userId;
        this.category = category;
        this.screenType = screenType;
        this.setup = setup;
        this.universe = universe;
        this.timeframe = timeframe;
        this.tradingDate = tradingDate;
        this.brokerAccountId = brokerAccountId;
        this.criteriaJson = criteriaJson;
        this.resultCount = resultCount;
        this.stage = stage;
        this.searchedAt = searchedAt;
    }

    public UUID getSearchId() { return searchId; }
    public UUID getUserId() { return userId; }
    public String getCategory() { return category; }
}
