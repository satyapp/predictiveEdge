package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;

class MarketIntelligenceInfrastructureConfigurationTest {
    @Test
    void parsesConfiguredTimeframesCaseInsensitivelyAndDeduplicatesThem() {
        assertThat(MarketIntelligenceInfrastructureConfiguration.parseTimeframes(
                "one_minute, FIVE_MINUTES,one_minute"))
                .containsExactly(BarTimeframe.ONE_MINUTE, BarTimeframe.FIVE_MINUTES);
    }

    @Test
    void requiresAtLeastOneTimeframe() {
        assertThatThrownBy(() -> MarketIntelligenceInfrastructureConfiguration.parseTimeframes(" , "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
