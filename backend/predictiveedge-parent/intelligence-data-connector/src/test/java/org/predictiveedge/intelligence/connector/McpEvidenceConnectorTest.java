package org.predictiveedge.intelligence.connector;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.predictiveedge.nse.mcp.NseMcpGateway;
import org.predictiveedge.nse.mcp.NseMcpServer;
import org.predictiveedge.trendlyne.mcp.TrendlyneMcpGateway;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

class McpEvidenceConnectorTest {
    private static final EvidenceQuery QUERY = EvidenceQuery.forSymbol(
            "infy", "Infosys fundamentals", Instant.parse("2026-11-13T06:30:00Z"));

    @Test
    void nseMarketDataUsesTheVerifiedCashMarketQuoteTool() {
        NseMcpGateway gateway = mock(NseMcpGateway.class);
        CallToolResult expected = CallToolResult.builder().content(java.util.List.of()).build();
        when(gateway.callTool(NseMcpServer.CM_MARKET, "cm_get_stock_quote", Map.of("symbol", "INFY")))
                .thenReturn(expected);

        new NseEvidenceConnector(gateway).fetch(EvidenceDomain.MARKET_DATA, QUERY);

        verify(gateway).callTool(NseMcpServer.CM_MARKET, "cm_get_stock_quote", Map.of("symbol", "INFY"));
    }

    @Test
    void trendlyneTechnicalAnalysisUsesTheVerifiedTechnicalView() {
        TrendlyneMcpGateway gateway = mock(TrendlyneMcpGateway.class);

        new TrendlyneEvidenceConnector(gateway).fetch(EvidenceDomain.TECHNICAL_ANALYSIS, QUERY);

        verify(gateway).callTool("get_overview_news_corp_events",
                Map.of("stock_code", "INFY", "type", "technical"));
    }
}
