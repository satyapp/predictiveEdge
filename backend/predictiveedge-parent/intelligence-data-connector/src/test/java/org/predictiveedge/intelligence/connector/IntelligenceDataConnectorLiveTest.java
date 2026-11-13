package org.predictiveedge.intelligence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.predictiveedge.nse.mcp.NseMcpConfiguration;
import org.predictiveedge.trendlyne.mcp.TrendlyneMcpConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class IntelligenceDataConnectorLiveTest {
    @Test
    @EnabledIfEnvironmentVariable(named = "PE_NSE_MCP_ENABLED", matches = "(?i)true")
    @EnabledIfEnvironmentVariable(named = "PE_TRENDLYNE_MCP_ENABLED", matches = "(?i)true")
    void marketDataFanOutUsesConfiguredNseAndTrendlyneConnections() {
        String trendlyneUrl = requiredEnvironment("PE_TRENDLYNE_MCP_URL");
        String bhavcopyUrl = requiredEnvironment("PE_NSE_MCP_BHAVCOPY_URL");
        String cmMarketUrl = requiredEnvironment("PE_NSE_MCP_CM_MARKET_URL");

        new ApplicationContextRunner()
                .withUserConfiguration(NseMcpConfiguration.class, TrendlyneMcpConfiguration.class,
                        IntelligenceConnectorConfiguration.class)
                .withPropertyValues(
                        "predictiveedge.nse.mcp.enabled=true",
                        "predictiveedge.nse.mcp.bhavcopy-url=" + bhavcopyUrl,
                        "predictiveedge.nse.mcp.cm-market-url=" + cmMarketUrl,
                        "predictiveedge.trendlyne.mcp.enabled=true",
                        "predictiveedge.trendlyne.mcp.url=" + trendlyneUrl)
                .run(context -> {
                    IntelligenceDataConnector connector = context.getBean(IntelligenceDataConnector.class);
                    var results = connector.fetch(EvidenceDomain.MARKET_DATA,
                            EvidenceQuery.forSymbol("INFY", "Infosys market data",
                                    Instant.parse("2026-11-13T06:30:00Z")));
                    assertThat(results).filteredOn(item -> item.source() == EvidenceSource.NSE)
                            .extracting(ProviderEvidence::status).containsExactly(EvidenceFetchStatus.SUCCESS);
                    assertThat(results).filteredOn(item -> item.source() == EvidenceSource.TRENDLYNE)
                            .extracting(ProviderEvidence::status).containsExactly(EvidenceFetchStatus.SUCCESS);
                });
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
