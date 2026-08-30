package org.predictiveedge.marketintelligence.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.predictiveedge.marketintelligence.domain.BarTimeframe;
import org.springframework.jdbc.core.JdbcTemplate;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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

    @Test
    void wiresTheGovernedCalendarPublicationUseCase() {
        var configuration = new MarketIntelligenceInfrastructureConfiguration();
        var port = configuration.marketSessionPublicationPort(mock(JdbcTemplate.class));

        assertThat(configuration.marketSessionCalendarService(port)).isNotNull();
    }

    @Test
    void wiresThePointInTimeMarketBarQueryUseCase() {
        var configuration = new MarketIntelligenceInfrastructureConfiguration();
        var port = configuration.marketBarQueryPort(mock(JdbcTemplate.class));

        assertThat(configuration.marketBarQueryService(port)).isNotNull();
    }

    @Test
    void wiresLowCardinalityOperationalMetrics() {
        var configuration = new MarketIntelligenceInfrastructureConfiguration();

        assertThat(configuration.marketIntelligenceMetrics(new SimpleMeterRegistry()))
                .isInstanceOf(MicrometerMarketIntelligenceMetrics.class);
    }
}
