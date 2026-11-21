package org.predictiveedge.nse.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.predictiveedge.chart.domain.MarketScreenType;

class NseMcpLiveMarketScreenSourceTest {
    @Test
    void enrichesMoverRowsWithQuotesAndBuildsCanonicalDataCopy() {
        var operations = new EnumMap<NseMcpServer, NseMcpOperations>(NseMcpServer.class);
        operations.put(NseMcpServer.BHAVCOPY, new StubOperations());
        operations.put(NseMcpServer.CM_MARKET, new StubOperations());
        var source = new NseMcpLiveMarketScreenSource(new NseMcpGateway(operations), new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-11-13T10:01:00Z"), ZoneOffset.UTC));

        var copy = source.fetch(MarketScreenType.TOP_GAINERS, 10);

        assertThat(copy.source()).isEqualTo("NSE_CM_MARKET_MCP:nse_get_gainers");
        assertThat(copy.freshness()).isEqualTo("LIVE");
        assertThat(copy.observations()).hasSize(1);
        assertThat(copy.observations().getFirst().symbol()).isEqualTo("EICHERMOT");
        assertThat(copy.observations().getFirst().open()).isEqualByComparingTo("7500");
        assertThat(copy.observations().getFirst().turnover()).isEqualByComparingTo("400000000");
        assertThat(copy.rejectedRows()).isEmpty();
    }

    @Test
    void reportsOnlyToolsActuallyAdvertisedByNseMcp() {
        var operations = new EnumMap<NseMcpServer, NseMcpOperations>(NseMcpServer.class);
        operations.put(NseMcpServer.BHAVCOPY, new StubOperations());
        operations.put(NseMcpServer.CM_MARKET, new StubOperations());
        var source = new NseMcpLiveMarketScreenSource(new NseMcpGateway(operations), new ObjectMapper(),
                Clock.systemUTC());

        assertThat(source.capabilities()).filteredOn(value -> value.screenType() == MarketScreenType.TOP_GAINERS)
                .allMatch(value -> value.available());
        assertThat(source.capabilities()).filteredOn(value -> value.screenType() == MarketScreenType.INTRADAY_OPPORTUNITY)
                .allMatch(value -> value.available() && value.sourceTool().equals("cm_get_equity_stocks"));
        assertThat(source.capabilities()).filteredOn(value -> value.screenType() == MarketScreenType.VOLUME_GAINERS)
                .allMatch(value -> !value.available());
    }

    @Test
    void reportsAdvertisedMoverToolUnavailableWhenItsLiveCacheIsEmpty() {
        var operations = new EnumMap<NseMcpServer, NseMcpOperations>(NseMcpServer.class);
        operations.put(NseMcpServer.BHAVCOPY, new StubOperations(false));
        operations.put(NseMcpServer.CM_MARKET, new StubOperations(false));
        var source = new NseMcpLiveMarketScreenSource(new NseMcpGateway(operations), new ObjectMapper(),
                Clock.systemUTC());

        assertThat(source.capabilities()).filteredOn(value -> value.screenType() == MarketScreenType.TOP_GAINERS)
                .allMatch(value -> !value.available() && value.detail().contains("last crawled: never"));
    }

    private static final class StubOperations implements NseMcpOperations {
        private final boolean moversAvailable;

        private StubOperations() { this(true); }
        private StubOperations(boolean moversAvailable) { this.moversAvailable = moversAvailable; }

        @Override public void initialize() { }
        @Override public ListToolsResult listTools() {
            return ListToolsResult.builder(List.of(Tool.builder("nse_get_gainers", Map.of()).build(),
                    Tool.builder("cm_get_data_status", Map.of()).build(),
                    Tool.builder("cm_get_equity_stocks", Map.of()).build(),
                    Tool.builder("cm_get_allstocks_status", Map.of()).build(),
                    Tool.builder("cm_get_stock_quote", Map.of()).build())).build();
        }
        @Override public CallToolResult callTool(String name, Map<String, Object> arguments) {
            String payload = switch (name) {
                case "nse_get_gainers" -> """
                    {"stocks":[{"symbol":"EICHERMOT","series":"EQ","ltp":7650,"prev_price":7548.85,
                    "perChange":1.34,"trade_quantity":52400,"turnover":400000000}]}
                    """;
                case "cm_get_stock_quote" -> """
                    {"symbol":"EICHERMOT","series":"EQ","ltp":7650,"open":7500,"high":7700,"low":7480,
                    "preClosePrice":7548.85,"percChange":1.34,"volume":52400,
                    "latestTimestamp":"2026-11-13T10:00:00Z"}
                    """;
                case "cm_get_allstocks_status" -> """
                    {"available":true,"lastCrawled":"2026-11-13T10:00:00Z"}
                    """;
                case "cm_get_data_status" -> moversAvailable ? """
                    {"indices":{"gainers":{"available":true,"lastCrawled":"2026-11-13T10:00:00Z"},
                    "loosers":{"available":true,"lastCrawled":"2026-11-13T10:00:00Z"}}}
                    """ : """
                    {"indices":{"gainers":{"available":false,"lastCrawled":"never"},
                    "loosers":{"available":false,"lastCrawled":"never"}}}
                    """;
                default -> throw new IllegalArgumentException(name);
            };
            return CallToolResult.builder().content(List.of(new TextContent(payload))).build();
        }
        @Override public void close() { }
    }
}
