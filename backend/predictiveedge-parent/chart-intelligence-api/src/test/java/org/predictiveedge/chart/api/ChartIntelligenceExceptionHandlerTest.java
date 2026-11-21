package org.predictiveedge.chart.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ChartIntelligenceExceptionHandlerTest {
    @Test
    void returnsStructuredServiceUnavailableInsteadOfFallingThroughToProtectedErrorDispatch() {
        var response = new ChartIntelligenceExceptionHandler()
                .dependencyUnavailable(new IllegalStateException("No data available. Market may be closed."));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "CHART_INTELLIGENCE_DEPENDENCY_UNAVAILABLE")
                .containsEntry("message", "No data available. Market may be closed.");
    }
}
