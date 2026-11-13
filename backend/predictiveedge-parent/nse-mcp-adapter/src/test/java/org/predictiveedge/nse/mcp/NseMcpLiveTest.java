package org.predictiveedge.nse.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class NseMcpLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PE_NSE_MCP_ENABLED", matches = "(?i)true")
    void officialEndpointsInitializeAndExposeTools() {
        Map<NseMcpServer, NseMcpOperations> operations = Map.of(
                NseMcpServer.BHAVCOPY,
                new SdkNseMcpOperations(environmentUri(
                        "PE_NSE_MCP_BHAVCOPY_URL", NseMcpProperties.DEFAULT_BHAVCOPY_URL), Duration.ofSeconds(30)),
                NseMcpServer.CM_MARKET,
                new SdkNseMcpOperations(environmentUri(
                        "PE_NSE_MCP_CM_MARKET_URL", NseMcpProperties.DEFAULT_CM_MARKET_URL), Duration.ofSeconds(30)));

        try (var gateway = new NseMcpGateway(operations)) {
            assertThat(gateway.listTools(NseMcpServer.BHAVCOPY)).isNotEmpty();
            assertThat(gateway.listTools(NseMcpServer.CM_MARKET)).isNotEmpty();
        }
    }

    private static URI environmentUri(String name, URI fallback) {
        String configured = System.getenv(name);
        return configured == null || configured.isBlank() ? fallback : URI.create(configured);
    }
}
