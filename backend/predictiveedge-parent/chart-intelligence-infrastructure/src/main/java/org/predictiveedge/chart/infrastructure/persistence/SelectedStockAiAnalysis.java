package org.predictiveedge.chart.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Stage-1 AI interpretation of one selected stock. Preliminary: never a final trading decision. */
@Entity
@Table(name = "selected_stock_ai_analysis", schema = "market_intelligence")
public class SelectedStockAiAnalysis {
    @EmbeddedId
    private AiAnalysisKey key;

    @Column(name = "stance", nullable = false)
    private String stance;

    @Column(name = "thesis", nullable = false)
    private String thesis;

    @Column(name = "confirmation", nullable = false)
    private String confirmation;

    @Column(name = "risk", nullable = false)
    private String risk;

    @Column(name = "model", nullable = false)
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "analysis_json", nullable = false)
    private String analysisJson;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected SelectedStockAiAnalysis() { }

    public SelectedStockAiAnalysis(AiAnalysisKey key, String stance, String thesis, String confirmation,
            String risk, String model, String analysisJson, Instant generatedAt) {
        this.key = key;
        this.stance = stance;
        this.thesis = thesis;
        this.confirmation = confirmation;
        this.risk = risk;
        this.model = model;
        this.analysisJson = analysisJson;
        this.generatedAt = generatedAt;
    }
}
