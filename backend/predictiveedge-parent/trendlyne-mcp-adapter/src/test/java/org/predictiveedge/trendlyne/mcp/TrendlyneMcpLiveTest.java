package org.predictiveedge.trendlyne.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class TrendlyneMcpLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PE_TRENDLYNE_MCP_ENABLED", matches = "(?i)true")
    void configuredEndpointInitializesAndExposesTools() {
        String configuredUrl = System.getenv("PE_TRENDLYNE_MCP_URL");
        assertThat(configuredUrl).as("PE_TRENDLYNE_MCP_URL").isNotBlank();

        var operations = new SdkTrendlyneMcpOperations(URI.create(configuredUrl), Duration.ofSeconds(30));
        try (var gateway = new TrendlyneMcpGateway(operations)) {
            assertThat(gateway.listTools()).isNotEmpty();
        }
    }
}
