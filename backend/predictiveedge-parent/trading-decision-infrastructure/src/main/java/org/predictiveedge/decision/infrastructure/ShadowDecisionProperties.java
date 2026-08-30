package org.predictiveedge.decision.infrastructure;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("predictiveedge.shadow-decision")
public class ShadowDecisionProperties {
    private boolean enabled;
    private UUID userId;
    private String venue = "NSE";
    private String instrumentId;
    private BigDecimal minimumDirectionalProbability = BigDecimal.valueOf(.55);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }
    public BigDecimal getMinimumDirectionalProbability() { return minimumDirectionalProbability; }
    public void setMinimumDirectionalProbability(BigDecimal value) { this.minimumDirectionalProbability = value; }
}
