package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evidence fetched for NEWS_AND_SENTIMENT, one row per selection, stage and source. */
@Entity
@Table(name = "selected_stock_evidence_news_and_sentiment", schema = "market_intelligence")
public class NewsAndSentimentEvidence extends EvidenceRow {
    protected NewsAndSentimentEvidence() { }

    public NewsAndSentimentEvidence(EvidenceKey key, String role, String status, Instant fetchedAt, String payload, String detail) {
        super(key, role, status, fetchedAt, payload, detail);
    }
}
