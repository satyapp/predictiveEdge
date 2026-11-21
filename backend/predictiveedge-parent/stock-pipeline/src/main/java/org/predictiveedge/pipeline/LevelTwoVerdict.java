package org.predictiveedge.pipeline;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Level-2 AI verdict for one stock, saved as an analysis run of kind LEVEL_TWO_AI. Advisory only: it is a
 * recommendation for the trader and for the FYERS Automate review loop, never an order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LevelTwoVerdict(
        String schemaVersion,
        String symbol,
        UUID levelOneRunId,
        String levelOneStance,
        Instant createdAt,
        String model,
        String action,
        BigDecimal entryLow,
        BigDecimal entryHigh,
        BigDecimal stopLoss,
        BigDecimal targetOne,
        BigDecimal targetTwo,
        Integer confidence,
        String horizon,
        Map<String, DomainView> domains,
        List<String> keyRisks,
        String rationale,
        List<String> validationNotes) {

    public static final String SCHEMA_VERSION = "level-two-ai-v1";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomainView(String view, String note) { }
}
