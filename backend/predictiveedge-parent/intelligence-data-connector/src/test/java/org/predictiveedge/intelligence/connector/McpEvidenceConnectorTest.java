package org.predictiveedge.intelligence.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.predictiveedge.nse.mcp.NseMcpGateway;
import org.predictiveedge.nse.mcp.NseMcpServer;
import org.predictiveedge.trendlyne.mcp.TrendlyneMcpGateway;

import io.modelcontextprotocol.spec.McpSchema;
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
    void trendlynePlanErrorIsUnavailableNotSuccess() {
        TrendlyneMcpGateway gateway = mock(TrendlyneMcpGateway.class);
        CallToolResult planError = CallToolResult.builder()
                .content(java.util.List.of(new McpSchema.TextContent(
                        "{\"status\": \"error\", \"message\": \"This tool is available on MCP Max, MCP Ultra only. [code:1013]\"}")))
                .isError(false)
                .build();
        when(gateway.callTool("get_stock_eod_ohlc", Map.of("stock_code", "INFY", "period", "monthly")))
                .thenReturn(planError);

        assertThatThrownBy(() -> new TrendlyneEvidenceConnector(gateway).fetch(EvidenceDomain.MARKET_DATA, QUERY))
                .isInstanceOf(EvidenceUnavailableException.class);
    }

    @Test
    void trendlyneToolErrorFlagIsUnavailable() {
        TrendlyneMcpGateway gateway = mock(TrendlyneMcpGateway.class);
        when(gateway.callTool("get_overview_news_corp_events", Map.of("stock_code", "INFY", "type", "news")))
                .thenReturn(CallToolResult.builder().content(java.util.List.of()).isError(true).build());

        assertThatThrownBy(() -> new TrendlyneEvidenceConnector(gateway).fetch(EvidenceDomain.NEWS_AND_SENTIMENT, QUERY))
                .isInstanceOf(EvidenceUnavailableException.class);
    }

    @Test
    void trendlyneDataResponseIsReturnedAsEvidence() {
        TrendlyneMcpGateway gateway = mock(TrendlyneMcpGateway.class);
        CallToolResult data = CallToolResult.builder()
                .content(java.util.List.of(new McpSchema.TextContent("stockData: ITI, Equity")))
                .isError(false)
                .build();
        when(gateway.callTool("get_overview_news_corp_events", Map.of("stock_code", "INFY", "type", "technical")))
                .thenReturn(data);

        assertThat(new TrendlyneEvidenceConnector(gateway).fetch(EvidenceDomain.TECHNICAL_ANALYSIS, QUERY))
                .isSameAs(data);
    }

    @Test
    void trendlyneTechnicalAnalysisUsesTheVerifiedTechnicalView() {
        TrendlyneMcpGateway gateway = mock(TrendlyneMcpGateway.class);

        new TrendlyneEvidenceConnector(gateway).fetch(EvidenceDomain.TECHNICAL_ANALYSIS, QUERY);

        verify(gateway).callTool("get_overview_news_corp_events",
                Map.of("stock_code", "INFY", "type", "technical"));
    }
}
